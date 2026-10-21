package br.gov.es.openpmo.repository;

import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionSource;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DynamicSelectionSourceRepository extends Neo4jRepository<DynamicSelectionSource, Long> {

  @Query("MATCH (s:DynamicSelectionSource) WHERE s.listKey = $listKey " +
         "AND ($parentCodes IS NULL OR size($parentCodes) = 0 OR s.parentCode IN $parentCodes) " +
         "RETURN s ORDER BY s.label")
  List<DynamicSelectionSource> findOptions(@Param("listKey") String listKey, @Param("parentCodes") List<String> parentCodes);

  @Query("MATCH (s:DynamicSelectionSource) WHERE s.listKey = $listKey AND s.code IN $codes RETURN s")
  List<DynamicSelectionSource> findByCodes(@Param("listKey") String listKey, @Param("codes") List<String> codes);

  @Query("MATCH (s:DynamicSelectionSource {listKey: $listKey, code: $code}) RETURN s")
  Optional<DynamicSelectionSource> findByCode(@Param("listKey") String listKey, @Param("code") String code);

  @Query("MATCH (s:DynamicSelectionSource {listKey: $listKey}) RETURN count(s)")
  long countByListKey(@Param("listKey") String listKey);

  /** Itens de listas que dependem de {@code listKey} e apontam para {@code code} como pai. */
  @Query("MATCH (l:DynamicSelectionList {dependsOnKey: $listKey}) " +
         "MATCH (s:DynamicSelectionSource {listKey: l.key, parentCode: $code}) RETURN count(s) > 0")
  boolean isParentOfAny(@Param("listKey") String listKey, @Param("code") String code);

  /** O codigo esta gravado em alguma ficha (valor da propriedade e a lista de codigos separada por virgula). */
  @Query("MATCH (p:DynamicSelection)-[:IS_DRIVEN_BY]->(:DynamicSelectionModel {providerKey: $listKey}) " +
         "WHERE p.value IS NOT NULL AND $code IN [c IN split(p.value, ',') | trim(c)] RETURN count(p) > 0")
  boolean isUsedInAnyProperty(@Param("listKey") String listKey, @Param("code") String code);

}
