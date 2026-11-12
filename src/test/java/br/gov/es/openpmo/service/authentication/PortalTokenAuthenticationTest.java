package br.gov.es.openpmo.service.authentication;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.gov.es.openpmo.dto.AcessoDto;
import br.gov.es.openpmo.enumerator.TokenType;
import br.gov.es.openpmo.model.actors.Person;
import br.gov.es.openpmo.service.actors.CitizenService;
import br.gov.es.openpmo.service.actors.IsAuthenticatedByService;
import br.gov.es.openpmo.service.actors.PersonService;
import br.gov.es.pmo.identity_parser.pmo_base.service.ProviderService;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

public class PortalTokenAuthenticationTest {

  private TokenService tokenService;
  private PersonService personService;
  private IsAuthenticatedByService isAuthenticatedByService;
  private AuthenticationService service;

  @Before
  public void setUp() {
    this.tokenService = mock(TokenService.class);
    this.personService = mock(PersonService.class);
    this.isAuthenticatedByService = mock(IsAuthenticatedByService.class);
    this.service = new AuthenticationService(
      this.tokenService, this.personService, this.isAuthenticatedByService,
      mock(ProviderService.class), mock(CitizenService.class)
    );
    ReflectionTestUtils.setField(this.service, "administrators", Collections.singletonList("admin@pb.gov.br"));
    when(this.tokenService.generateToken(any(), anyString(), any(), anyString(), eq(TokenType.AUTHENTICATION))).thenReturn("access");
    when(this.tokenService.generateToken(any(), anyString(), any(), anyString(), eq(TokenType.REFRESH))).thenReturn("refresh");
  }

  private static Jwt jwt(final String sub, final String email) {
    return new Jwt("raw", Instant.now(), Instant.now().plusSeconds(60),
      Collections.singletonMap("alg", "RS256"),
      sub == null
        ? Collections.singletonMap("email", email)
        : new java.util.HashMap<String, Object>() {{ put("sub", sub); put("email", email); put("name", "Pessoa"); }});
  }

  @Test
  public void emiteTokensParaPessoaEncontradaPeloSub() {
    final Person person = new Person();
    when(this.personService.findByKey("sub-1")).thenReturn(Optional.of(person));

    final AcessoDto dto = this.service.authenticatePortalToken(jwt("sub-1", "p@pb.gov.br"));

    assertNotNull(dto);
    assertEquals("access", dto.getToken());
    assertEquals("refresh", dto.getRefreshToken());
    verify(this.tokenService).generateToken(person, "sub-1", "p@pb.gov.br", AuthenticationService.PORTAL_RID, TokenType.AUTHENTICATION);
  }

  @Test
  public void usaOEmailQuandoOSubNaoEstaCadastrado() {
    final Person person = new Person();
    when(this.personService.findByKey("sub-novo")).thenReturn(Optional.empty());
    when(this.personService.findByEmail("p@pb.gov.br")).thenReturn(Optional.of(person));

    assertNotNull(this.service.authenticatePortalToken(jwt("sub-novo", "p@pb.gov.br")));
  }

  @Test
  public void bloqueiaQuemNaoTemCadastro() {
    when(this.personService.findByKey(anyString())).thenReturn(Optional.empty());
    when(this.personService.findByEmail(anyString())).thenReturn(Optional.empty());

    assertNull(this.service.authenticatePortalToken(jwt("sub-x", "x@pb.gov.br")));
    verify(this.personService, never()).save(any());
  }
}
