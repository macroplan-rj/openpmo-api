package br.gov.es.openpmo.service.reports;

import br.gov.es.openpmo.repository.PlanRepository;
import br.gov.es.openpmo.repository.WorkpackRepository;
import br.gov.es.openpmo.repository.permissions.PermissionRepository;
import br.gov.es.openpmo.service.actors.IGetPersonFromAuthorization;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class GetReportScope {
  private final PlanRepository planRepository;
  private final PermissionRepository permissionRepository;
  private final IGetPersonFromAuthorization getPersonFromAuthorization;
  private final WorkpackRepository workpackRepository;

  public GetReportScope(
          final PlanRepository planRepository,
          final PermissionRepository permissionRepository,
          IGetPersonFromAuthorization getPersonFromAuthorization, WorkpackRepository workpackRepository) {
    this.planRepository = planRepository;
    this.permissionRepository = permissionRepository;
    this.getPersonFromAuthorization = getPersonFromAuthorization;
    this.workpackRepository = workpackRepository;
  }

  public List<Long> execute(final List<Long> scope, final Long idPlan, final String authorization) {
    if (scope == null || scope.isEmpty()) {
      return new ArrayList<>();
    }
    final List<Long> scopeResponse = new ArrayList<>();
    final IGetPersonFromAuthorization.PersonDataResponse personData = this.getPersonFromAuthorization.execute(authorization);
    final boolean isAdministrator = personData.getPerson().getAdministrator();
    boolean hasPermissionOfficeOrPlan = this.permissionRepository.hasPermissionOfficeOrPlanByIdPlan(idPlan, personData.getKey());
    if (isAdministrator || hasPermissionOfficeOrPlan) {
      if (scope.size() == 1 && scope.get(0).equals(idPlan)) {
        final List<Long> workpacksIds = this.planRepository.findWorkpacksBelongsToPlan(idPlan);
        if (workpacksIds != null && !workpacksIds.isEmpty()) {
          scopeResponse.addAll(workpacksIds);
        }
        return this.somenteVivos(scopeResponse);
      }
      scopeResponse.addAll(scope);
      final List<Long> workpacksChildren = this.workpackRepository.idsWorkpacksChildren(scope);
      if (workpacksChildren != null && !workpacksChildren.isEmpty()) {
        scopeResponse.addAll(workpacksChildren);
      }
      return this.somenteVivos(scopeResponse);
    }
    if (scope.size() == 1 && scope.get(0).equals(idPlan)) {
      final List<Long> workpacksWithPermission = this.planRepository.findWorkpacksBelongsToPlanWithPermission(idPlan, personData.getKey());
      if (workpacksWithPermission != null && !workpacksWithPermission.isEmpty()) {
        scopeResponse.addAll(workpacksWithPermission);
      }
      final List<Long> workpacksChildren = this.workpackRepository.idsWorkpacksChildren(workpacksWithPermission);
      if (workpacksChildren != null && !workpacksChildren.isEmpty()) {
        scopeResponse.addAll(workpacksChildren);
      }
      return this.somenteVivos(scopeResponse);
    }

    for (final Long scopeId : scope) {
      final List<Long> workpacksIds = new ArrayList<>();
      workpacksIds.add(scopeId);
      boolean hasPermissionWorkpackSelfOrParents = this.permissionRepository.hasPermissionWorkpackSelfOrParents(scopeId, personData.getKey());
      if (hasPermissionWorkpackSelfOrParents) {
        scopeResponse.add(scopeId);
        final List<Long> workpacksChildren = this.workpackRepository.idsWorkpacksChildren(workpacksIds);
        if (workpacksChildren != null && !workpacksChildren.isEmpty()) {
          scopeResponse.addAll(workpacksChildren);
        }
      } else {
        final List<Long> workpacksChildrenWithPermission = this.permissionRepository.idsWorkpacksChildrenWithPermission(workpacksIds, personData.getKey());
        if (workpacksChildrenWithPermission != null && !workpacksChildrenWithPermission.isEmpty()) {
          scopeResponse.addAll(workpacksChildrenWithPermission);
          final List<Long> workpacksChildren = this.workpackRepository.idsWorkpacksChildren(workpacksChildrenWithPermission);
          if (workpacksChildren != null && !workpacksChildren.isEmpty()) {
            scopeResponse.addAll(workpacksChildren);
          }
        }
      }
    }
    return this.somenteVivos(scopeResponse);
  }

  /**
   * Descarta do escopo o que nao existe mais ou esta marcado como deletado, e
   * remove duplicatas.
   *
   * Sem isto, o ramo que recebe ids explicitos fazia addAll(scope) sem conferir
   * nada: um id de workpack deletado atravessava o filtro de permissao, chegava
   * ao Cypher e produzia ficha para um projeto que a tela nao lista mais. O ramo
   * que expande o plano ja filtrava, o que fazia os dois caminhos discordarem
   * sobre o mesmo plano.
   *
   * Devolver lista vazia aqui e o que faz o ReportScopeValidator recusar com
   * mensagem tratada, em vez de o relatorio sair em branco com HTTP 200.
   */
  private List<Long> somenteVivos(final List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return new ArrayList<>();
    }
    final List<Long> distintos = new ArrayList<>(new LinkedHashSet<>(ids));
    final List<Long> vivos = this.workpackRepository.idsExistentesNaoDeletados(distintos);
    return vivos == null ? new ArrayList<>() : vivos;
  }

}
