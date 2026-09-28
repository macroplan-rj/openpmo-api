package br.gov.es.openpmo.repository;

import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionSource;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DynamicSelectionSourceRepository extends Neo4jRepository<DynamicSelectionSource, Long> {

  @Query("MATCH (s:DynamicSelectionSource) WHERE s.listKey = $listKey " +
         "AND ($parentCodes IS NULL OR size($parentCodes) = 0 OR s.parentCode IN $parentCodes) " +
         "RETURN s ORDER BY s.label")
  List<DynamicSelectionSource> findOptions(@Param("listKey") String listKey, @Param("parentCodes") List<String> parentCodes);

  @Query("MATCH (s:DynamicSelectionSource) WHERE s.listKey = $listKey AND s.code IN $codes RETURN s")
  List<DynamicSelectionSource> findByCodes(@Param("listKey") String listKey, @Param("codes") List<String> codes);

}
