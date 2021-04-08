package com.jws1g18.myphrplus;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.google.firebase.auth.FirebaseAuth;

@Component
public class FirebaseTokenFilter extends OncePerRequestFilter{

    /**
     * Checks if the firebase header is present, if so check the token, if token is valid add to security context 
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        final String token =  request.getHeader("Xx-Firebase-Id-Token");
        if(StringUtils.hasText(token)){
            String uid = parseToken(token);
            if(uid != null){
                Authentication auth = new FirebaseAuthenticationToken(uid, token);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Parses a Firebase token and retrives the User ID
     * @param idToken
     * @return
     */
    private String parseToken(String idToken) {
		try {
			return  FirebaseAuth.getInstance().verifyIdToken(idToken).getUid();
		} catch (Exception e) {
			return null;
		}
	}
    
}
