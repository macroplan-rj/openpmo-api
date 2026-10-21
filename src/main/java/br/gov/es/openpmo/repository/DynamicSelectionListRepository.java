package br.gov.es.openpmo.repository;

import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionList;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DynamicSelectionListRepository extends Neo4jRepository<DynamicSelectionList, Long> {

  @Query("MATCH (l:DynamicSelectionList) RETURN l ORDER BY l.key")
  List<DynamicSelectionList> findAllOrdered();

  @Query("MATCH (l:DynamicSelectionList {key: $key}) RETURN l")
  Optional<DynamicSelectionList> findByKey(@Param("key") String key);

}
