package br.gov.es.openpmo.repository;

import br.gov.es.openpmo.model.painel.PainelApiToken;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PainelApiTokenRepository extends Neo4jRepository<PainelApiToken, Long> {

  @Query("MATCH (t:PainelApiToken) RETURN t ORDER BY t.createdAt DESC")
  List<PainelApiToken> findAllOrdered();

  @Query("MATCH (t:PainelApiToken {tokenHash: $hash}) WHERE t.revokedAt IS NULL RETURN t LIMIT 1")
  Optional<PainelApiToken> findActiveByHash(@Param("hash") String hash);

}
