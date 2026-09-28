package br.gov.es.openpmo.model.properties.dynamicselection;

import br.gov.es.openpmo.model.Entity;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * Um item de uma lista interna ({@link DynamicSelectionList}): codigo, rotulo e codigo do pai
 * na lista de que esta depende (null quando a lista nao depende de outra).
 */
@NodeEntity
public class DynamicSelectionSource extends Entity {

  private String listKey;

  private String code;

  private String label;

  private String parentCode;

  public String getListKey() { return this.listKey; }

  public void setListKey(final String listKey) { this.listKey = listKey; }

  public String getCode() { return this.code; }

  public void setCode(final String code) { this.code = code; }

  public String getLabel() { return this.label; }

  public void setLabel(final String label) { this.label = label; }

  public String getParentCode() { return this.parentCode; }

  public void setParentCode(final String parentCode) { this.parentCode = parentCode; }

}
