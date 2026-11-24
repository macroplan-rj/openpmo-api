package br.gov.es.openpmo.configuration.security;

import br.gov.es.openpmo.service.actors.PersonService;
import br.gov.es.openpmo.service.authentication.TokenService;
import br.gov.es.openpmo.service.painel.PainelApiTokenService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Acesso a /painel/* pelo X-Api-Key: token gerado na administracao (banco) ou token fixo da variavel (US-042). */
public class SecurityFilterPainelTest {

  private static final String FIXO = "token-fixo-da-variavel";

  private PainelApiTokenService tokens;

  @Before
  public void setUp() {
    this.tokens = mock(PainelApiTokenService.class);
    when(this.tokens.isValid("pmo_ativo")).thenReturn(true);
    when(this.tokens.isValid("pmo_revogado")).thenReturn(false);
  }

  private SecurityFilter filtro(final String fixo) {
    return new SecurityFilter(mock(TokenService.class), mock(PersonService.class), "keycloak", fixo, this.tokens);
  }

  /** Devolve o status; 200 significa que a requisicao seguiu na cadeia. */
  private static int chamar(final SecurityFilter filtro, final String url, final String key) throws Exception {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", url);
    if(key != null) {
      request.addHeader("X-Api-Key", key);
    }
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();
    filtro.doFilterInternal(request, response, chain);
    if(response.getStatus() == 401) {
      assertNull(chain.getRequest());
      assertTrue(response.getContentAsString().contains("invalid.token"));
    }
    else {
      assertNotNull(chain.getRequest());
    }
    return response.getStatus();
  }

  @Test
  public void tokenAtivoDoBancoPassa() throws Exception {
    assertEquals(200, chamar(this.filtro(""), "/openpmo/painel/filtros", "pmo_ativo"));
  }

  @Test
  public void tokenRevogadoRecebe401() throws Exception {
    assertEquals(401, chamar(this.filtro(""), "/openpmo/painel/filtros", "pmo_revogado"));
  }

  @Test
  public void semTokenRecebe401SemConsultarOBanco() throws Exception {
    assertEquals(401, chamar(this.filtro(FIXO), "/openpmo/painel/filtros", null));
    verify(this.tokens, never()).isValid(anyString());
  }

  @Test
  public void tokenFixoDaVariavelContinuaValendo() throws Exception {
    assertEquals(200, chamar(this.filtro(FIXO), "/openpmo/painel/carteira", FIXO));
    verify(this.tokens, never()).isValid(anyString());
  }

  @Test
  public void tokenFixoDeixaDeValerComAVariavelVazia() throws Exception {
    assertEquals(401, chamar(this.filtro(""), "/openpmo/painel/carteira", FIXO));
  }

  @Test
  public void gestaoDeTokensNaoAceitaXApiKey() throws Exception {
    // /painel-tokens exige o JWT do usuario: sem Authorization, 401 mesmo com um X-Api-Key valido.
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/openpmo/painel-tokens");
    request.addHeader("X-Api-Key", "pmo_ativo");
    final MockHttpServletResponse response = new MockHttpServletResponse();
    final MockFilterChain chain = new MockFilterChain();
    this.filtro(FIXO).doFilterInternal(request, response, chain);
    assertEquals(401, response.getStatus());
    assertNull(chain.getRequest());
  }

}
