package br.gov.es.openpmo.controller.painel;

import br.gov.es.openpmo.service.painel.PainelFiltro;
import br.gov.es.openpmo.service.painel.PainelService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * API de dados do Painel Estrategico, consumida pelo front-end externo (SD #10801). Somente leitura; acesso pelo
 * token de servico no cabecalho X-Api-Key (SecurityFilter). Contrato: docs/API_PAINEL_ESTRATEGICO.md.
 */
@Api
@RestController
@RequestMapping("/painel")
public class PainelController {

  private final PainelService service;

  public PainelController(final PainelService service) {
    this.service = service;
  }

  @ApiOperation("Opcoes dos filtros")
  @GetMapping("/filtros")
  public Map<String, Object> filtros(
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dataPosicao
  ) {
    return this.service.filtros(PainelFiltro.de(null, null, null, null, null, dataPosicao, null, null, null));
  }

  @ApiOperation("Painel gerencial (SD #10795)")
  @GetMapping("/carteira")
  public Map<String, Object> carteira(
    @RequestParam(required = false) final String area,
    @RequestParam(required = false) final String objetivo,
    @RequestParam(required = false) final String projeto,
    @RequestParam(required = false) final String instituicao,
    @RequestParam(required = false) final String horizonte,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dataPosicao
  ) {
    return this.service.carteira(PainelFiltro.de(area, objetivo, projeto, instituicao, horizonte, dataPosicao, null, null, null));
  }

  @ApiOperation("Painel executivo, aba Entregas realizadas (SD #10796)")
  @GetMapping("/executivo/entregas")
  public Map<String, Object> executivoEntregas(
    @RequestParam(required = false) final String area,
    @RequestParam(required = false) final String objetivo,
    @RequestParam(required = false) final String projeto,
    @RequestParam(required = false) final String instituicao,
    @RequestParam(required = false) final String horizonte,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dataPosicao,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate periodoInicio,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate periodoFim,
    @RequestParam(required = false, defaultValue = "CONCLUIDAS") final String modo
  ) {
    return this.service.executivoEntregas(
      PainelFiltro.de(area, objetivo, projeto, instituicao, horizonte, dataPosicao, periodoInicio, periodoFim, modo));
  }

  @ApiOperation("Painel executivo, aba Onde atuar (SD #10797)")
  @GetMapping("/executivo/onde-atuar")
  public Map<String, Object> executivoOndeAtuar(
    @RequestParam(required = false) final String area,
    @RequestParam(required = false) final String objetivo,
    @RequestParam(required = false) final String projeto,
    @RequestParam(required = false) final String instituicao,
    @RequestParam(required = false) final String horizonte,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dataPosicao
  ) {
    return this.service.executivoOndeAtuar(PainelFiltro.de(area, objetivo, projeto, instituicao, horizonte, dataPosicao, null, null, null));
  }

  @ApiOperation("Visao por projeto (SD #10798)")
  @GetMapping("/projetos/{id}")
  public Map<String, Object> projeto(
    @PathVariable final Long id,
    @RequestParam(required = false) final String area,
    @RequestParam(required = false) final String objetivo,
    @RequestParam(required = false) final String instituicao,
    @RequestParam(required = false) final String horizonte,
    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate dataPosicao
  ) {
    return this.service.projeto(id, PainelFiltro.de(area, objetivo, null, instituicao, horizonte, dataPosicao, null, null, null));
  }

}
