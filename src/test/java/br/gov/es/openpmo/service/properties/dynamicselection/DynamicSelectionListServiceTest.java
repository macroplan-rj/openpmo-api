package br.gov.es.openpmo.service.properties.dynamicselection;

import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionList;
import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionSource;
import br.gov.es.openpmo.repository.DynamicSelectionListRepository;
import br.gov.es.openpmo.repository.DynamicSelectionSourceRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** US-029 — cadastro dos itens das listas da Selecao dinamica (SD #10548 / #10607). */
public class DynamicSelectionListServiceTest {

  private DynamicSelectionListRepository listas;
  private DynamicSelectionSourceRepository itens;
  private DynamicSelectionListService service;

  private static DynamicSelectionList lista(final String key, final String pai) {
    final DynamicSelectionList l = new DynamicSelectionList();
    l.setKey(key);
    l.setLabel(key);
    l.setDependsOnKey(pai);
    return l;
  }

  private static DynamicSelectionSource item(final String listKey, final String code, final String pai) {
    final DynamicSelectionSource s = new DynamicSelectionSource();
    s.setListKey(listKey);
    s.setCode(code);
    s.setLabel("Item " + code);
    s.setParentCode(pai);
    return s;
  }

  private static DynamicSelectionListService.ItemRequest pedido(final String code, final String label, final String pai) {
    final DynamicSelectionListService.ItemRequest r = new DynamicSelectionListService.ItemRequest();
    r.setCode(code);
    r.setLabel(label);
    r.setParentCode(pai);
    return r;
  }

  private static void assertRecusa(final String chave, final Runnable acao) {
    try {
      acao.run();
      fail("esperava NegocioException " + chave);
    } catch (final NegocioException e) {
      assertTrue(e.getMessage(), e.getMessage().startsWith(chave));
    }
  }

  @Before
  public void setUp() {
    this.listas = mock(DynamicSelectionListRepository.class);
    this.itens = mock(DynamicSelectionSourceRepository.class);
    when(this.listas.findByKey(anyString())).thenReturn(Optional.empty());
    when(this.listas.findByKey("ppa.programa")).thenReturn(Optional.of(lista("ppa.programa", null)));
    when(this.listas.findByKey("ppa.indicador")).thenReturn(Optional.of(lista("ppa.indicador", "ppa.programa")));
    when(this.itens.findByCode(anyString(), anyString())).thenReturn(Optional.empty());
    when(this.itens.findByCode("ppa.programa", "5001")).thenReturn(Optional.of(item("ppa.programa", "5001", null)));
    when(this.itens.save(any(DynamicSelectionSource.class))).thenAnswer(inv -> inv.getArgument(0));
    this.service = new DynamicSelectionListService(this.listas, this.itens);
  }

  @Test
  public void listasComTotalDeItens() {
    when(this.listas.findAllOrdered()).thenReturn(Collections.singletonList(lista("ppa.programa", null)));
    when(this.itens.countByListKey("ppa.programa")).thenReturn(22L);
    final List<DynamicSelectionListService.ListSummary> resumo = this.service.lists();
    assertEquals(1, resumo.size());
    assertEquals(22L, resumo.get(0).getItems());
  }

  @Test
  public void incluiItemComPaiExistenteEAparaEspacos() {
    final DynamicSelectionSource novo = this.service.create("ppa.indicador", pedido(" I900 ", " Novo indicador ", "5001"));
    assertEquals("ppa.indicador", novo.getListKey());
    assertEquals("I900", novo.getCode());
    assertEquals("Novo indicador", novo.getLabel());
    assertEquals("5001", novo.getParentCode());
  }

  @Test
  public void recusaCodigoDuplicadoOuVazio() {
    assertRecusa(DynamicSelectionListService.DUPLICATED_CODE, () -> this.service.create("ppa.programa", pedido("5001", "X", null)));
    assertRecusa(DynamicSelectionListService.CODE_REQUIRED, () -> this.service.create("ppa.programa", pedido("  ", "X", null)));
  }

  @Test
  public void recusaRotuloVazio() {
    assertRecusa(DynamicSelectionListService.LABEL_REQUIRED, () -> this.service.create("ppa.programa", pedido("5099", " ", null)));
  }

  @Test
  public void exigePaiQuandoAListaDependeEProibeQuandoNao() {
    assertRecusa(DynamicSelectionListService.PARENT_REQUIRED, () -> this.service.create("ppa.indicador", pedido("I1", "X", null)));
    assertRecusa(DynamicSelectionListService.PARENT_NOT_FOUND, () -> this.service.create("ppa.indicador", pedido("I1", "X", "9999")));
    assertRecusa(DynamicSelectionListService.PARENT_NOT_ALLOWED, () -> this.service.create("ppa.programa", pedido("5099", "X", "5001")));
  }

  @Test
  public void recusaListaInexistente() {
    assertRecusa(DynamicSelectionListService.LIST_NOT_FOUND, () -> this.service.items("nao.existe"));
  }

  @Test
  public void editaRotuloSemTrocarCodigo() {
    final DynamicSelectionSource editado = this.service.update("ppa.programa", "5001", pedido("OUTRO", "Gestao renomeada", null));
    assertEquals("5001", editado.getCode());
    assertEquals("Gestao renomeada", editado.getLabel());
    assertRecusa(DynamicSelectionListService.ITEM_NOT_FOUND, () -> this.service.update("ppa.programa", "7777", pedido(null, "X", null)));
  }

  @Test
  public void naoExcluiPaiDeOutrosItens() {
    when(this.itens.isParentOfAny("ppa.programa", "5001")).thenReturn(true);
    assertRecusa(DynamicSelectionListService.ITEM_IS_PARENT, () -> this.service.delete("ppa.programa", "5001"));
    verify(this.itens, never()).delete(any(DynamicSelectionSource.class));
  }

  @Test
  public void naoExcluiItemGravadoEmFicha() {
    when(this.itens.isUsedInAnyProperty("ppa.programa", "5001")).thenReturn(true);
    assertRecusa(DynamicSelectionListService.ITEM_IN_USE, () -> this.service.delete("ppa.programa", "5001"));
    verify(this.itens, never()).delete(any(DynamicSelectionSource.class));
  }

  @Test
  public void excluiItemLivre() {
    this.service.delete("ppa.programa", "5001");
    verify(this.itens).delete(any(DynamicSelectionSource.class));
  }

  private static DynamicSelectionListService.ListRequest novaLista(final String label, final String pai) {
    final DynamicSelectionListService.ListRequest r = new DynamicSelectionListService.ListRequest();
    r.setLabel(label);
    r.setDependsOnKey(pai);
    return r;
  }

  private void comListas() {
    when(this.listas.findAllOrdered()).thenReturn(java.util.Arrays.asList(lista("pelp.objetivo", null), lista("lista.metas", "pelp.objetivo")));
    when(this.listas.save(any(DynamicSelectionList.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  public void criaListaComChaveGeradaSemAcentoEDependencia() {
    this.comListas();
    final DynamicSelectionListService.ListSummary nova = this.service.createList(novaLista(" Estratégias de Desenvolvimento ", "pelp.objetivo"));
    assertEquals("lista.estrategias-de-desenvolvimento", nova.getKey());
    assertEquals("Estratégias de Desenvolvimento", nova.getLabel());
    assertEquals("pelp.objetivo", nova.getDependsOnKey());
    assertEquals(0L, nova.getItems());
  }

  @Test
  public void chaveJaUsadaGanhaSufixo() {
    this.comListas();
    assertEquals("lista.metas-2", this.service.createList(novaLista("Metas!", null)).getKey());
  }

  @Test
  public void recusaNomeVazioRepetidoOuPaiInexistente() {
    this.comListas();
    assertRecusa(DynamicSelectionListService.LIST_LABEL_REQUIRED, () -> this.service.createList(novaLista("  ", null)));
    assertRecusa(DynamicSelectionListService.LIST_DUPLICATED_LABEL, () -> this.service.createList(novaLista("PELP.OBJETIVO", null)));
    assertRecusa(DynamicSelectionListService.LIST_PARENT_NOT_FOUND, () -> this.service.createList(novaLista("Nova", "nao.existe")));
    verify(this.listas, never()).save(any(DynamicSelectionList.class));
  }

}
