package br.gov.es.openpmo.dto.painel;

import br.gov.es.openpmo.model.painel.PainelApiToken;

/** Resposta da geracao: os dados do token e o valor em claro, devolvido somente aqui. */
public class PainelApiTokenCreatedDto extends PainelApiTokenDto {

  private final String token;

  public PainelApiTokenCreatedDto(final PainelApiToken entity, final String token) {
    super(entity);
    this.token = token;
  }

  public String getToken() { return this.token; }

}
