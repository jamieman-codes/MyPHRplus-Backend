package com.jws1g18.myphrplus;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public class FirebaseAuthenticationToken extends AbstractAuthenticationToken{
    private static final long serialVersionUID = -155623123571721302L;
    private final Object principal;
	private Object credentials;


    public FirebaseAuthenticationToken(Object principal, Object credentials) {
		super(null);
		this.principal = principal;
		this.credentials = credentials;
		setAuthenticated(true);
	}

    @Override
    public Object getCredentials() {
        return this.credentials; 
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }

    @Override
	public void eraseCredentials() {
		super.eraseCredentials();
		credentials = null;
	}
    
}
