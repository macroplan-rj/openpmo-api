package br.gov.es.openpmo.service.authentication;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Valida o access token do Keycloak recebido do portal SIPGR (handoff).
 *
 * O portal e o OpenPMO usam o mesmo realm: a assinatura RS256 é conferida pelo JWKS do realm e o
 * {@code iss} precisa ser o issuer configurado. Reaproveita as propriedades do login OIDC
 * ({@code provider.keycloak.jwk-set-uri} e {@code provider.idsvr.issuer-uri}); ambas podem ser
 * sobrescritas por {@code app.portal.jwk-set-uri} e {@code app.portal.issuer-uri}.
 */
@Component
public class PortalTokenValidator {

  private final String jwkSetUri;
  private final String issuerUri;
  private volatile JwtDecoder decoder;

  public PortalTokenValidator(
    @Value("${app.portal.jwk-set-uri:${spring.security.oauth2.client.provider.keycloak.jwk-set-uri:}}") final String jwkSetUri,
    @Value("${app.portal.issuer-uri:${spring.security.oauth2.client.provider.idsvr.issuer-uri:}}") final String issuerUri
  ) {
    this.jwkSetUri = jwkSetUri;
    this.issuerUri = issuerUri;
  }

  PortalTokenValidator(final JwtDecoder decoder, final String issuerUri) {
    this.jwkSetUri = null;
    this.issuerUri = issuerUri;
    this.decoder = decoder;
  }

  /** Devolve as claims do token ou vazio se ele for inválido (assinatura, issuer ou expiração). */
  public Optional<Jwt> validate(final String token) {
    if(token == null || token.trim().isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(this.getDecoder().decode(token.trim()));
    }
    catch(final JwtException | IllegalStateException e) {
      return Optional.empty();
    }
  }

  private JwtDecoder getDecoder() {
    if(this.decoder == null) {
      synchronized(this) {
        if(this.decoder == null) {
          if(this.jwkSetUri == null || this.jwkSetUri.isEmpty() || this.issuerUri == null || this.issuerUri.isEmpty()) {
            throw new IllegalStateException("JWKS/issuer do Keycloak não configurados");
          }
          final NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(this.jwkSetUri).build();
          nimbus.setJwtValidator(validators(this.issuerUri));
          this.decoder = nimbus;
        }
      }
    }
    return this.decoder;
  }

  static OAuth2TokenValidator<Jwt> validators(final String issuerUri) {
    return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), new JwtIssuerValidator(issuerUri));
  }

}
