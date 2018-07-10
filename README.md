# À propos de l'application Support

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Conseil Régional Nord Pas de Calais - Picardie, Conseil départemental de l'Essonne, Conseil régional d'Aquitaine-Limousin-Poitou-Charentes, , Conseil départemental de Seine-Et-Marne, Conseil régional d'Île-de-France, Ville de Paris
* Développeur(s) : CGI
* Financeur(s) : Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, Département 77, Région Île-de-France, Ville de Paris
* Description : Application permettant l'automatisation de l'échange de tickets support entre l'application support de l'ENT, l'outil IWS, et JIRA. 

## Déployer dans ent-core
<pre>
		build.sh clean install
</pre>

# Présentation du module

Ce module permet l'échange de tickets entre différents outils de ticketing.

Les outils supportés sont :
* le module support de l'ENT ( mini v1.1)
* L'outil IWS d'ISILOG
* JIRA d'Attlassian

Flux de création autorisés :
  ENT -> IWS
  IWS -> JIRA
  IWS -> ENT   : interdit
  ENT -> JIRA  : interdit
  JIRA -> *    : interdit
  
Flux de Mise à jour autorisés :
  ENT -> IWS
  ENT -> JIRA
  IWS -> ENT
  IWS -> JIRA
  JIRA -> ENT
  JIRA -> IWS
           


## Fonctionnalités

Le module expose 
           sur le BUS une interface permettant d'escalader un ticket vers IWS.
           une route permettant à IWS d'escalader des tickets vers le module support de l'ENT et/ou JIRA.
           une route permettant à JIRA d'escalader un ticket vers IWS et le mosule support de l'ENT (maj uniquement)

Le module exploite 
           sur le BUS l'interface du module Support pour escalader les mise à jour provenant de IWS ou JIRA
           l'emailSender du springboard pour l'escalade vers IWS depuis le module support ou JIRA.  

## Configuration

pré-requis : un mail sender doit être configurer sur la plateforme.

Contenu du fichier deployment/support/conf.json.template :

     {
          "name": "fr.openent~supportpivot~1.1-SNAPSHOT",
          "config": {
            "main" : "fr.openent.supportpivot.Supportpivot",
            "port" : 9595,
            "app-name" : "Supportpivot",
        	"app-address" : "/supportpivot",
        	"app-icon" : "Supportpivot-large",
            "host": "${host}",
            "ssl" : $ssl,
            "auto-redeploy": false,
            "userbook-host": "${host}",
            "app-registry.port" : 8012,
            "mode" : "${mode}",
            "entcore.port" : 8009,
    		"collectivity" : "${supportPivotCollectivity}",
    		"academy" : "${supportPivotAcademy}",
    		"default-attribution" : "${supportPivotDefAttribution}",
    		"default-tickettype" : "${supportPivotDefTicketType}",
    		"default-priority" : "${supportPivotDefTicketPriority}",
            "mail-iws" : "${supportPivotIWSMail}",
            "jira-login" : "${supportPivotJIRALogin}",
            "jira-passwd" : "${supportPivotJIRAPwd}",
            "jira-host" : "${supportPivotJIRAHost}",
            "jira-url" : "/rest/api/2/issue/",
            "jira-project-key" :  "${supportPivotJIRAProjectKey}",
            "jira-allowed-tickettype" :  "${supportPivotJIRAAllowedTicketType}",
            "jira-allowed-priority":  "${supportPivotJIRAAllowedPriority}",
            "jira-custom-fields" : {
                "id_ent" : "${supportPivotCFIdEnt}",
                "id_iws" : "${supportPivotCFIdIws}",
                "status_ent" : "${supportPivotCFStEnt}",
                "status_iws" : "${supportPivotCFStIws}",
                "creation" : "${supportPivotCFCreateDate}",
                "resolution_ent" : "${supportPivotCFResEnt}",
                "resolution_iws" : "${supportPivotCFResIws}",
                "creator" : "${supportPivotCFCreator}",
                "response_technical" : "${supportPivotCFTechResp}"
            },
        	"jira-status-mapping": {
        		"statutsJira": ${supportPivotMappingStatus},
        		"statutsDefault" : "${supportPivotDefaultStatus}"
        	}
          }
        }


Les paramètres spécifiques à l'application support sont les suivants :

    mod parameter           :  conf.properties variable         ,   usage
    -------------------------------------------------------------------------------------------------------------------
    -------------------------------------------------------------------------------------------------------------------
    "collectivity"          : "${supportPivotCollectivity}"  , collectivity name 
    "academy"               : "${supportPivotAcademy}"  , academy name
    "default-attribution"   : "${supportPivotDefAttribution}"   , default attribution among ENT, RECTORAT, CGI
    "default-tickettype"    : "${supportPivotDefTicketType}"    , default ticket type
    "default-priority"      : "${supportPivotDefTicketPriority}", default ticket priority
    -------------------------------------------------------------------------------------------------------------------
                IWS escalating
    -------------------------------------------------------------------------------------------------------------------
    "mail-iws"              : "${supportPivotIWSMail}"          , mail adress to escalate ticket to IWS
    -------------------------------------------------------------------------------------------------------------------
                JIRA escalating
    -------------------------------------------------------------------------------------------------------------------
    "jira-login"                : "${supportPivotJIRALogin"                 , JIRA login 
    "jira-passwd"               : "${supportPivotJIRAPwd}"                  , JIRA password
    "jira-host"                 : "${supportPivotJIRAHost}"                 , JIRA host  ex: http://mysite.com:8080/jira
    "jira-project-key"          :  "${supportPivotJIRAProjectKey}"          , JIRA key of dest project
    "jira-allowed-tickettype"   :  "${supportPivotJIRAAllowedTicketType}"   , JIRA icket type i.e [bug, task] 
    "jira-allowed-priority"     :  "${supportPivotJIRAAllowedPriority}"     , Order 3 priorities [low, mid, high]
    -------------------------------------------------------------------------------------------------------------------
                JIRA Custom fields used to display IWS informations
    -------------------------------------------------------------------------------------------------------------------
    "id_ent"                : "${supportPivotCFIdEnt}"          , Jira field id for ENT id of the ticket
    "id_iws"                : "${supportPivotCFIdIws}"          , Jira field id for IWS id of the ticket
    "status_ent"            : "${supportPivotCFStEnt}"          , Jira field id for ENT status of the ticket
    "status_iws"            : "${supportPivotCFStIws}"          , Jira field id for IWS id of the ticket
    "creation"              : "${supportPivotCFCreateDate}"     , Jira field id for IWS creation date
    "resolution_ent"        : "${supportPivotCFResEnt}"         , Jira field id for ENT resolution date
    "resolution_iws"        : "${supportPivotCFResIws}"         , Jira field id for IWS resolution date
    "creator"               : "${supportPivotCFCreator}"        , Jira field id for creator of the ticket
    "response_technical"    : "${supportPivotCFTechResp}"       , Jira field id for CGI technical response
    "statutsJira"           : ${supportPivotMappingStatus}      , Status Mapping table. give for each JIRA status a 
                                                                  corresponding expected IWS status
                                                                   ex. [ "Nouveau": ["JiraStatus1"],
                                                                         "Ouvert": ["JiraStatus2","JiraStatus3"],
                                                                         "Résolu": ["JiraStatus4"],
                                                                         "Fermé": ["JiraStatus5"]   ]             
    "statutsDefault"        : "${supportPivotDefaultStatut}"                 , Default statut sent to IWS if no corresponding found 
                                                                   in mapping table above
