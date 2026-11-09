package unisimon.demo.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/** Convierte el claim "rol" del JWT en la autoridad ROLE_xxx que usan las anotaciones @PreAuthorize. */
@Component
public class RolJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String rol = jwt.getClaimAsString(JwtTokenService.CLAIM_ROL);
        List<SimpleGrantedAuthority> autoridades = rol == null
                ? List.of()
                : List.of(new SimpleGrantedAuthority("ROLE_" + rol));
        return new JwtAuthenticationToken(jwt, autoridades, jwt.getSubject());
    }
}
