package br.gov.es.openpmo.repository;

import br.gov.es.openpmo.model.properties.dynamicselection.DynamicSelectionList;
import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.List;

public interface DynamicSelectionListRepository extends Neo4jRepository<DynamicSelectionList, Long> {

  @Query("MATCH (l:DynamicSelectionList) RETURN l ORDER BY l.key")
  List<DynamicSelectionList> findAllOrdered();

}
