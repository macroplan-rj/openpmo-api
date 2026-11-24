package br.gov.es.openpmo.model.painel;

import br.gov.es.openpmo.model.Entity;
import org.neo4j.ogm.annotation.NodeEntity;

import java.time.LocalDateTime;

/**
 * Token de servico da API do Painel Estrategico (/painel/*), gerado e revogado pela tela de administracao.
 * O valor do token nao e guardado: fica so o hash SHA-256 e um prefixo para identificacao na lista.
 */
@NodeEntity
public class PainelApiToken extends Entity {

  private String name;

  private String tokenHash;

  private String prefix;

  private LocalDateTime createdAt;

  private String createdBy;

  private LocalDateTime revokedAt;

  private String revokedBy;

  private LocalDateTime lastUsedAt;

  public String getName() { return this.name; }

  public void setName(final String name) { this.name = name; }

  public String getTokenHash() { return this.tokenHash; }

  public void setTokenHash(final String tokenHash) { this.tokenHash = tokenHash; }

  public String getPrefix() { return this.prefix; }

  public void setPrefix(final String prefix) { this.prefix = prefix; }

  public LocalDateTime getCreatedAt() { return this.createdAt; }

  public void setCreatedAt(final LocalDateTime createdAt) { this.createdAt = createdAt; }

  public String getCreatedBy() { return this.createdBy; }

  public void setCreatedBy(final String createdBy) { this.createdBy = createdBy; }

  public LocalDateTime getRevokedAt() { return this.revokedAt; }

  public void setRevokedAt(final LocalDateTime revokedAt) { this.revokedAt = revokedAt; }

  public String getRevokedBy() { return this.revokedBy; }

  public void setRevokedBy(final String revokedBy) { this.revokedBy = revokedBy; }

  public LocalDateTime getLastUsedAt() { return this.lastUsedAt; }

  public void setLastUsedAt(final LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

  public boolean isActive() { return this.revokedAt == null; }

}
