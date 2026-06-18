package com.atlas.booking.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  private static final String REALM_ACCESS = "realm_access";
  private static final String ROLES = "roles";
  private static final String ROLES_PREFIX = "ROLE_";

  @Override
  public Collection<GrantedAuthority> convert(Jwt source) {
    Map<String, Object> realmAccess = (Map<String, Object>) source.getClaims().get(REALM_ACCESS);
    if (realmAccess == null || realmAccess.isEmpty()) {
      return new ArrayList<>();
    }
    return ((List<String>) realmAccess.get(ROLES))
        .stream().map(roleName -> ROLES_PREFIX + roleName)
        .map(SimpleGrantedAuthority::new)
        .collect(Collectors.toList());
  }

}
