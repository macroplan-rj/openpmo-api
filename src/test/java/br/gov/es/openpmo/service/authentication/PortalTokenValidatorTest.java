package br.gov.es.openpmo.service.authentication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class PortalTokenValidatorTest {

  private static final String ISSUER = "https://keycloak.example/realms/sipgr";
  private static KeyPair keys;
  private static KeyPair otherKeys;

  @BeforeClass
  public static void generateKeys() throws Exception {
    final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keys = generator.generateKeyPair();
    otherKeys = generator.generateKeyPair();
  }

  private static PortalTokenValidator validator() {
    final NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
    decoder.setJwtValidator(PortalTokenValidator.validators(ISSUER));
    return new PortalTokenValidator(decoder, ISSUER);
  }

  private static String token(final KeyPair signingKeys, final String issuer, final long expiresInMs) throws Exception {
    final JWTClaimsSet claims = new JWTClaimsSet.Builder()
      .subject("8f0c-sub")
      .issuer(issuer)
      .claim("email", "pessoa@pb.gov.br")
      .claim("name", "Pessoa Teste")
      .issueTime(new Date())
      .expirationTime(new Date(System.currentTimeMillis() + expiresInMs))
      .build();
    final SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
    jwt.sign(new RSASSASigner(signingKeys.getPrivate()));
    return jwt.serialize();
  }

  @Test
  public void aceitaTokenDoRealm() throws Exception {
    final String token = token(keys, ISSUER, 60_000);
    assertTrue(validator().validate(token).isPresent());
    assertEquals("pessoa@pb.gov.br", validator().validate(token).get().getClaimAsString("email"));
  }

  @Test
  public void recusaAssinaturaDeOutraChave() throws Exception {
    assertFalse(validator().validate(token(otherKeys, ISSUER, 60_000)).isPresent());
  }

  @Test
  public void recusaOutroIssuer() throws Exception {
    assertFalse(validator().validate(token(keys, "https://outro.example/realms/x", 60_000)).isPresent());
  }

  @Test
  public void recusaTokenExpirado() throws Exception {
    assertFalse(validator().validate(token(keys, ISSUER, -3_600_000)).isPresent());
  }

  @Test
  public void recusaTokenVazioOuMalformado() {
    assertFalse(validator().validate(null).isPresent());
    assertFalse(validator().validate("  ").isPresent());
    assertFalse(validator().validate("abc.def.ghi").isPresent());
  }

  @Test
  public void semConfiguracaoRecusaSemLancarExcecao() {
    assertFalse(new PortalTokenValidator("", "").validate("abc.def.ghi").isPresent());
  }
}
