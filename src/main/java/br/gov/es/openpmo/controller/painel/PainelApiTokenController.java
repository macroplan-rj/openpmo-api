package br.gov.es.openpmo.controller.painel;

import br.gov.es.openpmo.configuration.Authorization;
import br.gov.es.openpmo.dto.ResponseBase;
import br.gov.es.openpmo.dto.painel.PainelApiTokenCreatedDto;
import br.gov.es.openpmo.dto.painel.PainelApiTokenDto;
import br.gov.es.openpmo.dto.painel.PainelApiTokenStoreDto;
import br.gov.es.openpmo.service.actors.PersonService;
import br.gov.es.openpmo.service.authentication.TokenService;
import br.gov.es.openpmo.service.painel.PainelApiTokenService;
import br.gov.es.openpmo.service.permissions.canaccess.ICanAccessService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Gestao dos tokens de servico da API do Painel Estrategico pela tela de administracao (US-042).
 * Somente administrador. O caminho fica fora de /painel/ de proposito: aqui vale o JWT do usuario, nao o X-Api-Key.
 */
@Api
@RestController
@CrossOrigin
@RequestMapping("/painel-tokens")
public class PainelApiTokenController {

  private final PainelApiTokenService service;

  private final ICanAccessService canAccessService;

  private final TokenService tokenService;

  private final PersonService personService;

  public PainelApiTokenController(
    final PainelApiTokenService service,
    final ICanAccessService canAccessService,
    final TokenService tokenService,
    final PersonService personService
  ) {
    this.service = service;
    this.canAccessService = canAccessService;
    this.tokenService = tokenService;
    this.personService = personService;
  }

  @ApiOperation("Lista os tokens da API do Painel (sem o valor)")
  @GetMapping
  public ResponseEntity<ResponseBase<List<PainelApiTokenDto>>> findAll(@Authorization final String authorization) {
    this.canAccessService.ensureIsAdministrator(authorization);
    final List<PainelApiTokenDto> tokens = this.service.findAll().stream()
      .map(PainelApiTokenDto::new)
      .collect(Collectors.toList());
    return ResponseEntity.ok(ResponseBase.of(tokens));
  }

  @ApiOperation("Gera um token; o valor so e devolvido nesta resposta")
  @PostMapping
  public ResponseEntity<ResponseBase<PainelApiTokenCreatedDto>> generate(
    @RequestBody final PainelApiTokenStoreDto request,
    @Authorization final String authorization
  ) {
    this.canAccessService.ensureIsAdministrator(authorization);
    final PainelApiTokenService.GeneratedToken generated =
      this.service.generate(request.getName(), this.personName(authorization));
    return ResponseEntity.ok(ResponseBase.of(new PainelApiTokenCreatedDto(generated.getToken(), generated.getValue())));
  }

  @ApiOperation("Revoga um token")
  @DeleteMapping("/{id}")
  public ResponseEntity<ResponseBase<PainelApiTokenDto>> revoke(
    @PathVariable final Long id,
    @Authorization final String authorization
  ) {
    this.canAccessService.ensureIsAdministrator(authorization);
    return ResponseEntity.ok(ResponseBase.of(new PainelApiTokenDto(this.service.revoke(id, this.personName(authorization)))));
  }

  private String personName(final String authorization) {
    return this.personService.findById(this.tokenService.getUserId(authorization)).getName();
  }

}
