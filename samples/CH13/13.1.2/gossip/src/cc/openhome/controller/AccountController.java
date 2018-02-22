package cc.openhome.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import cc.openhome.model.Account;
import cc.openhome.model.EmailService;
import cc.openhome.model.UserService;

@Controller
public class AccountController {
    private String REDIRECT_INDEX_PATH = "/gossip";
    private String REGISTER_SUCCESS_PATH = "/WEB-INF/jsp/register_success.jsp";
    private String REGISTER_FORM_PATH = "/WEB-INF/jsp/register.jsp";
    private String VERIFY_PATH = "/WEB-INF/jsp/verify.jsp";
    private String FORGOT_PATH = "/WEB-INF/jsp/forgot.jsp";
    private String RESET_PASSWORD_FORM_PATH = "/WEB-INF/jsp/reset_password.jsp";
    private String RESET_PASSWORD_SUCCESS_PATH = "/WEB-INF/jsp/reset_success.jsp";

    private final Pattern emailRegex = Pattern.compile(
            "^[_a-z0-9-]+([.][_a-z0-9-]+)*@[a-z0-9-]+([.][a-z0-9-]+)*$");
    private final Pattern passwdRegex = Pattern.compile("^\\w{8,16}$");
    private final Pattern usernameRegex = Pattern.compile("^\\w{1,16}$");
        
    @RequestMapping(value = "register", method = RequestMethod.GET)
    public void registerForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher(REGISTER_FORM_PATH)
               .forward(request, response);
    }
    
    @RequestMapping(value = "register", method = RequestMethod.POST)
    public void register(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String email = request.getParameter("email");
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        String password2 = request.getParameter("password2");

        List<String> errors = new ArrayList<>(); 
        if (!validateEmail(email)) {
            errors.add("未填寫郵件或格式不正確");
        }
        if(!validateUsername(username)) {
            errors.add("未填寫使用者名稱或格式不正確");
        }
        if (!validatePassword(password, password2)) {
            errors.add("請確認密碼符合格式並再度確認密碼");
        }
        
        String path;
        if(errors.isEmpty()) {
            path = REGISTER_SUCCESS_PATH;
            
            UserService userService = (UserService) request.getServletContext().getAttribute("userService");
            EmailService emailService = (EmailService) request.getServletContext().getAttribute("emailService");
            Optional<Account> optionalAcct = userService.tryCreateUser(email, username, password);
            if(optionalAcct.isPresent()) {
                emailService.validationLink(optionalAcct.get());
            } else {
                emailService.failedRegistration(username, email);
            }
        } else {
            path = REGISTER_FORM_PATH;
            request.setAttribute("errors", errors);
        }

        request.getRequestDispatcher(path).forward(request, response);
    }    
    
    @RequestMapping("verify")
    public void verify(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = request.getParameter("email");
        String token = request.getParameter("token");
        UserService userService = (UserService) request.getServletContext().getAttribute("userService");
        request.setAttribute("acct", userService.verify(email, token));
        request.getRequestDispatcher(VERIFY_PATH).forward(request, response);
    }
    
    @RequestMapping(value = "forgot", method = RequestMethod.POST)
    public void forgot(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        
        UserService userService =
                (UserService) request.getServletContext().getAttribute("userService");
        Optional<Account> optionalAcct = userService.accountByNameEmail(name, email);
        
        if(optionalAcct.isPresent()) {
            EmailService emailService = 
                    (EmailService) request.getServletContext().getAttribute("emailService");
            emailService.passwordResetLink(optionalAcct.get());
        }
        
        request.setAttribute("email", email);
        request.getRequestDispatcher(FORGOT_PATH)
               .forward(request, response);
    }
    
    @RequestMapping(value = "reset_password", method = RequestMethod.GET)
    public void resetPasswordForm(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String token = request.getParameter("token");
        
        UserService userService = (UserService) request.getServletContext().getAttribute("userService");
        Optional<Account> optionalAcct = userService.accountByNameEmail(name, email);
        
        if(optionalAcct.isPresent()) {
            Account acct = optionalAcct.get();
            if(acct.getPassword().equals(token)) {
                request.setAttribute("acct", acct);
                request.getSession().setAttribute("token", token);
                request.getRequestDispatcher(RESET_PASSWORD_FORM_PATH)
                       .forward(request, response);
                return;
            }
        }
        response.sendRedirect(REDIRECT_INDEX_PATH);
    }
    
    @RequestMapping(value = "reset_password", method = RequestMethod.POST)
    public void resetPassword(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = request.getParameter("token");
        String storedToken = (String) request.getSession().getAttribute("token");
        if(storedToken == null || !storedToken.equals(token)) {
            response.sendRedirect("/gossip");
            return;
        }
        
        String name = request.getParameter("name");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String password2 = request.getParameter("password2");
            
        UserService userService = (UserService) request.getServletContext().getAttribute("userService");

        if (!validatePassword(password, password2)) {
            Optional<Account> optionalAcct = userService.accountByNameEmail(name, email);
            request.setAttribute("errors", Arrays.asList("請確認密碼符合格式並再度確認密碼"));
            request.setAttribute("acct", optionalAcct.get());

            request.getRequestDispatcher(RESET_PASSWORD_FORM_PATH)
                   .forward(request, response);
        } else {
            userService.resetPassword(name, password);
            request.getRequestDispatcher(RESET_PASSWORD_SUCCESS_PATH)
                   .forward(request, response);
        }    
    }
    
    private boolean validateEmail(String email) {
        return email != null && emailRegex.matcher(email).find();
    }
    
    private boolean validateUsername(String username) {
        return username != null && usernameRegex.matcher(username).find();
    }

    
    private boolean validatePassword(String password, String password2) {
        return password != null && 
               passwdRegex.matcher(password).find() && 
               password.equals(password2);
    }    
}
