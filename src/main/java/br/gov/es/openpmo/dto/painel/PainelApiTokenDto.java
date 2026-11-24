package br.gov.es.openpmo.dto.painel;

import br.gov.es.openpmo.model.painel.PainelApiToken;

import java.time.LocalDateTime;

/** Token da API do Painel como aparece na lista da administracao; nunca traz o valor nem o hash. */
public class PainelApiTokenDto {

  private final Long id;

  private final String name;

  private final String prefix;

  private final LocalDateTime createdAt;

  private final String createdBy;

  private final LocalDateTime revokedAt;

  private final String revokedBy;

  private final LocalDateTime lastUsedAt;

  private final boolean active;

  public PainelApiTokenDto(final PainelApiToken token) {
    this.id = token.getId();
    this.name = token.getName();
    this.prefix = token.getPrefix();
    this.createdAt = token.getCreatedAt();
    this.createdBy = token.getCreatedBy();
    this.revokedAt = token.getRevokedAt();
    this.revokedBy = token.getRevokedBy();
    this.lastUsedAt = token.getLastUsedAt();
    this.active = token.isActive();
  }

  public Long getId() { return this.id; }

  public String getName() { return this.name; }

  public String getPrefix() { return this.prefix; }

  public LocalDateTime getCreatedAt() { return this.createdAt; }

  public String getCreatedBy() { return this.createdBy; }

  public LocalDateTime getRevokedAt() { return this.revokedAt; }

  public String getRevokedBy() { return this.revokedBy; }

  public LocalDateTime getLastUsedAt() { return this.lastUsedAt; }

  public boolean isActive() { return this.active; }

}
