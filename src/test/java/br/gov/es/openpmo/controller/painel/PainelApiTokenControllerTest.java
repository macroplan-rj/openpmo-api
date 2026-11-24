package br.gov.es.openpmo.controller.painel;

import br.gov.es.openpmo.dto.ResponseBase;
import br.gov.es.openpmo.dto.painel.PainelApiTokenCreatedDto;
import br.gov.es.openpmo.dto.painel.PainelApiTokenDto;
import br.gov.es.openpmo.dto.painel.PainelApiTokenStoreDto;
import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.actors.Person;
import br.gov.es.openpmo.model.painel.PainelApiToken;
import br.gov.es.openpmo.repository.PainelApiTokenRepository;
import br.gov.es.openpmo.service.actors.PersonService;
import br.gov.es.openpmo.service.authentication.TokenService;
import br.gov.es.openpmo.service.painel.PainelApiTokenService;
import br.gov.es.openpmo.service.permissions.canaccess.ICanAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Endpoints de gestao dos tokens da API do Painel: so administrador, e a lista nunca traz o valor (US-042). */
public class PainelApiTokenControllerTest {

  private static final String AUTH = "Bearer jwt";

  private PainelApiTokenRepository repository;
  private ICanAccessService canAccess;
  private PainelApiTokenController controller;

  @Before
  public void setUp() {
    this.repository = mock(PainelApiTokenRepository.class);
    when(this.repository.save(any(PainelApiToken.class))).thenAnswer(i -> i.getArgument(0));
    this.canAccess = mock(ICanAccessService.class);
    final TokenService tokenService = mock(TokenService.class);
    when(tokenService.getUserId(AUTH)).thenReturn(5L);
    final PersonService personService = mock(PersonService.class);
    final Person ana = mock(Person.class);
    when(ana.getName()).thenReturn("Ana");
    when(personService.findById(5L)).thenReturn(ana);
    this.controller = new PainelApiTokenController(
      new PainelApiTokenService(this.repository), this.canAccess, tokenService, personService);
  }

  private static PainelApiTokenStoreDto pedido(final String nome) {
    final PainelApiTokenStoreDto dto = new PainelApiTokenStoreDto();
    dto.setName(nome);
    return dto;
  }

  @Test
  public void administradorGeraERecebeOValorUmaVez() throws Exception {
    final ResponseBase<PainelApiTokenCreatedDto> resposta = this.controller.generate(pedido("Painel externo"), AUTH).getBody();

    final PainelApiTokenCreatedDto dto = resposta.getData();
    assertTrue(dto.getToken().startsWith("pmo_"));
    assertEquals("Painel externo", dto.getName());
    assertEquals("Ana", dto.getCreatedBy());
    assertTrue(dto.isActive());
    assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(dto).contains("tokenHash"));
  }

  @Test
  public void listaNaoTrazValorNemHash() throws Exception {
    final PainelApiToken token = new PainelApiToken();
    token.setName("Painel externo");
    token.setPrefix("pmo_abcdef");
    token.setTokenHash("hash-secreto");
    when(this.repository.findAllOrdered()).thenReturn(Collections.singletonList(token));

    final List<PainelApiTokenDto> lista = this.controller.findAll(AUTH).getBody().getData();

    assertEquals(1, lista.size());
    final String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(lista);
    assertTrue(json.contains("pmo_abcdef"));
    assertFalse(json.contains("hash-secreto"));
    assertFalse(json.contains("\"token\""));
  }

  @Test
  public void administradorRevoga() {
    final PainelApiToken token = new PainelApiToken();
    when(this.repository.findById(7L)).thenReturn(Optional.of(token));

    final PainelApiTokenDto dto = this.controller.revoke(7L, AUTH).getBody().getData();

    assertFalse(dto.isActive());
    assertEquals("Ana", dto.getRevokedBy());
  }

  @Test
  public void naoAdministradorNaoListaNaoGeraNaoRevoga() {
    doThrow(new NegocioException("sem acesso")).when(this.canAccess).ensureIsAdministrator(AUTH);

    for(final Runnable acao : new Runnable[] {
      () -> this.controller.findAll(AUTH),
      () -> this.controller.generate(pedido("x"), AUTH),
      () -> this.controller.revoke(7L, AUTH)
    }) {
      try {
        acao.run();
        fail("deveria negar o acesso");
      }
      catch(final NegocioException esperado) {
        assertEquals("sem acesso", esperado.getMessage());
      }
    }
    verifyNoInteractions(this.repository);
  }

}
