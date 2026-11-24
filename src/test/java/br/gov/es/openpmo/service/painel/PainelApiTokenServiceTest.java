package br.gov.es.openpmo.service.painel;

import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.exception.RegistroNaoEncontradoException;
import br.gov.es.openpmo.model.painel.PainelApiToken;
import br.gov.es.openpmo.repository.PainelApiTokenRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Tokens de servico da API do Painel gerados pela administracao (US-042). */
public class PainelApiTokenServiceTest {

  private PainelApiTokenRepository repository;
  private PainelApiTokenService service;

  private static void falha(final Class<? extends RuntimeException> esperada, final Runnable acao) {
    try {
      acao.run();
    }
    catch(final RuntimeException e) {
      assertTrue("esperava " + esperada.getSimpleName() + ", veio " + e, esperada.isInstance(e));
      return;
    }
    fail("esperava " + esperada.getSimpleName());
  }

  @Before
  public void setUp() {
    this.repository = mock(PainelApiTokenRepository.class);
    when(this.repository.save(any(PainelApiToken.class))).thenAnswer(i -> i.getArgument(0));
    this.service = new PainelApiTokenService(this.repository);
  }

  @Test
  public void geraTokenEGuardaSoOHash() {
    final PainelApiTokenService.GeneratedToken gerado = this.service.generate("  Painel externo  ", "Ana");

    final ArgumentCaptor<PainelApiToken> salvo = ArgumentCaptor.forClass(PainelApiToken.class);
    verify(this.repository).save(salvo.capture());
    final PainelApiToken token = salvo.getValue();

    assertTrue(gerado.getValue().startsWith("pmo_"));
    assertEquals(4 + 43, gerado.getValue().length());
    assertEquals("Painel externo", token.getName());
    assertEquals("Ana", token.getCreatedBy());
    assertEquals(PainelApiTokenService.hash(gerado.getValue()), token.getTokenHash());
    assertNotEquals(gerado.getValue(), token.getTokenHash());
    assertTrue(gerado.getValue().startsWith(token.getPrefix()));
    assertTrue(token.getPrefix().length() < gerado.getValue().length());
    assertNotNull(token.getCreatedAt());
    assertTrue(token.isActive());
  }

  @Test
  public void tokensGeradosSaoDiferentes() {
    assertNotEquals(this.service.generate("a", "Ana").getValue(), this.service.generate("a", "Ana").getValue());
  }

  @Test
  public void nomeVazioOuLongoERecusado() {
    falha(NegocioException.class, () -> this.service.generate("   ", "Ana"));
    falha(NegocioException.class, () -> this.service.generate(null, "Ana"));
    final StringBuilder longo = new StringBuilder();
    for(int i = 0; i <= PainelApiTokenService.NAME_MAX_LENGTH; i++) {
      longo.append('x');
    }
    falha(NegocioException.class, () -> this.service.generate(longo.toString(), "Ana"));
    verify(this.repository, never()).save(any());
  }

  @Test
  public void tokenAtivoValidaERegistraUltimoUso() {
    final PainelApiToken token = new PainelApiToken();
    when(this.repository.findActiveByHash(PainelApiTokenService.hash("pmo_abc"))).thenReturn(Optional.of(token));

    assertTrue(this.service.isValid("pmo_abc"));
    assertNotNull(token.getLastUsedAt());
    verify(this.repository).save(token);
  }

  @Test
  public void ultimoUsoRecenteNaoGravaDeNovo() {
    final PainelApiToken token = new PainelApiToken();
    token.setLastUsedAt(LocalDateTime.now().minusMinutes(1));
    when(this.repository.findActiveByHash(anyString())).thenReturn(Optional.of(token));

    assertTrue(this.service.isValid("pmo_abc"));
    verify(this.repository, never()).save(any());
  }

  @Test
  public void tokenRevogadoOuDesconhecidoNaoValida() {
    when(this.repository.findActiveByHash(anyString())).thenReturn(Optional.empty());

    assertFalse(this.service.isValid("pmo_revogado"));
    assertFalse(this.service.isValid(null));
    assertFalse(this.service.isValid("sem-prefixo"));
    verify(this.repository, times(1)).findActiveByHash(anyString());
  }

  @Test
  public void revogarMarcaDataEAutor() {
    final PainelApiToken token = new PainelApiToken();
    when(this.repository.findById(7L)).thenReturn(Optional.of(token));

    final PainelApiToken revogado = this.service.revoke(7L, "Bia");

    assertFalse(revogado.isActive());
    assertEquals("Bia", revogado.getRevokedBy());
    verify(this.repository).save(token);
  }

  @Test
  public void revogarDeNovoMantemARevogacaoOriginal() {
    final PainelApiToken token = new PainelApiToken();
    final LocalDateTime antes = LocalDateTime.now().minusDays(1);
    token.setRevokedAt(antes);
    token.setRevokedBy("Bia");
    when(this.repository.findById(7L)).thenReturn(Optional.of(token));

    this.service.revoke(7L, "Caio");

    assertEquals(antes, token.getRevokedAt());
    assertEquals("Bia", token.getRevokedBy());
    verify(this.repository, never()).save(any());
  }

  @Test
  public void revogarInexistenteFalha() {
    when(this.repository.findById(9L)).thenReturn(Optional.empty());
    falha(RegistroNaoEncontradoException.class, () -> this.service.revoke(9L, "Bia"));
  }

}
