package br.gov.es.openpmo.service.painel;

import br.gov.es.openpmo.exception.NegocioException;
import br.gov.es.openpmo.exception.RegistroNaoEncontradoException;
import br.gov.es.openpmo.model.painel.PainelApiToken;
import br.gov.es.openpmo.repository.PainelApiTokenRepository;
import br.gov.es.openpmo.utils.ApplicationMessage;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/** Gestao dos tokens de servico da API do Painel Estrategico: gerar, listar, revogar e validar. */
@Service
public class PainelApiTokenService {

  static final String TOKEN_PREFIX = "pmo_";

  static final int NAME_MAX_LENGTH = 100;

  private static final int VISIBLE_PREFIX_LENGTH = 10;

  /** O ultimo uso e gravado no maximo uma vez por intervalo, para nao escrever no banco a cada chamada. */
  private static final Duration LAST_USED_INTERVAL = Duration.ofMinutes(5);

  private final PainelApiTokenRepository repository;

  private final SecureRandom random = new SecureRandom();

  public PainelApiTokenService(final PainelApiTokenRepository repository) {
    this.repository = repository;
  }

  public static String hash(final String value) {
    try {
      final byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      final StringBuilder hex = new StringBuilder(digest.length * 2);
      for(final byte b : digest) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    }
    catch(final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Gera o token e devolve o valor em claro; e a unica vez em que ele fica disponivel. */
  public GeneratedToken generate(final String name, final String createdBy) {
    final String trimmed = name == null ? "" : name.trim();
    if(trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
      throw new NegocioException(ApplicationMessage.PAINEL_TOKEN_NAME_INVALID);
    }
    final byte[] bytes = new byte[32];
    this.random.nextBytes(bytes);
    final String value = TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    final PainelApiToken token = new PainelApiToken();
    token.setName(trimmed);
    token.setTokenHash(hash(value));
    token.setPrefix(value.substring(0, VISIBLE_PREFIX_LENGTH));
    token.setCreatedAt(LocalDateTime.now());
    token.setCreatedBy(createdBy);
    return new GeneratedToken(value, this.repository.save(token));
  }

  public List<PainelApiToken> findAll() {
    return this.repository.findAllOrdered();
  }

  public PainelApiToken revoke(final Long id, final String revokedBy) {
    final PainelApiToken token = this.repository.findById(id)
      .orElseThrow(() -> new RegistroNaoEncontradoException(ApplicationMessage.PAINEL_TOKEN_NOT_FOUND));
    if(!token.isActive()) {
      return token;
    }
    token.setRevokedAt(LocalDateTime.now());
    token.setRevokedBy(revokedBy);
    return this.repository.save(token);
  }

  /** Verdadeiro se o valor recebido corresponde a um token ativo; registra o ultimo uso. */
  public boolean isValid(final String value) {
    if(value == null || !value.startsWith(TOKEN_PREFIX)) {
      return false;
    }
    final Optional<PainelApiToken> found = this.repository.findActiveByHash(hash(value));
    if(!found.isPresent()) {
      return false;
    }
    final PainelApiToken token = found.get();
    final LocalDateTime now = LocalDateTime.now();
    if(token.getLastUsedAt() == null || token.getLastUsedAt().plus(LAST_USED_INTERVAL).isBefore(now)) {
      token.setLastUsedAt(now);
      this.repository.save(token);
    }
    return true;
  }

  public static final class GeneratedToken {

    private final String value;

    private final PainelApiToken token;

    GeneratedToken(final String value, final PainelApiToken token) {
      this.value = value;
      this.token = token;
    }

    public String getValue() { return this.value; }

    public PainelApiToken getToken() { return this.token; }

  }

}
