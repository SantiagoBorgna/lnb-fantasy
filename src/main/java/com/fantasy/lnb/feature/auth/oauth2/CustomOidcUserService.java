package com.fantasy.lnb.feature.auth.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final OAuth2UserService oauth2UserService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. Delegar al comportamiento por defecto de Spring para obtener el OidcUser
        OidcUser oidcUser = super.loadUser(userRequest);
        
        // 2. Extraer el provider (ej. "azure")
        String provider = userRequest.getClientRegistration().getRegistrationId();
        
        // 3. Reutilizar la logica de persistencia que ya tenemos en OAuth2UserService
        oauth2UserService.procesarUsuario(oidcUser, provider);
        
        return oidcUser;
    }
}
