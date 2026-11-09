#!/usr/bin/env python3
"""Render production resources only after verifying both internal image digests."""
import argparse,json,pathlib,re
p=argparse.ArgumentParser();p.add_argument('--web-digest',required=True);p.add_argument('--api-digest',required=True);p.add_argument('--output',required=True,type=pathlib.Path);a=p.parse_args()
for digest in [a.web_digest,a.api_digest]:
 if not re.fullmatch(r'sha256:[a-f0-9]{64}',digest):raise SystemExit('Immutable sha256 digest required')
ns='alisa-infra';registry='registry.kind.internal:5000/';items=[]
def resource(kind,name,spec=None,data=None,wave=None):
 x={'apiVersion':{'Deployment':'apps/v1','Job':'batch/v1','HTTPRoute':'gateway.networking.k8s.io/v1'}.get(kind,'v1'),'kind':kind,'metadata':{'name':name,'namespace':ns}}
 if wave is not None:x['metadata']['annotations']={'argocd.argoproj.io/sync-wave':str(wave)}
 if spec is not None:x['spec']=spec
 if data is not None:x['data']=data
 items.append(x);return x
x=resource('PersistentVolumeClaim','alisa-admin-auth-backups',{'accessModes':['ReadWriteOnce'],'resources':{'requests':{'storage':'2Gi'}}},wave=-20);x['metadata']['annotations']['argocd.argoproj.io/sync-options']='Prune=false,Delete=false'
config='''server:
  forward-headers-strategy: framework
  servlet:
    session:
      cookie:
        name: ALISA_ADMIN_SESSION
        path: /admin/auth
        secure: true
        http-only: true
        same-site: lax
spring:
  application:
    name: alisa-admin-api
  cloud:
    discovery:
      enabled: false
    nacos:
      discovery:
        enabled: false
        register-enabled: false
      config:
        enabled: false
alisa:
  tenancy:
    enabled: true
  authorization:
    enabled: true
    administrators:
      - ${PORTAL_BOOTSTRAP_ADMINISTRATOR}
    applications:
      portal: tenant
  oauth:
    enabled: true
    issuer: https://alisa.vip.cpolar.cn/admin/auth
    applications:
      portal:
        "[portal:console:manage]": 管理门户资源
'''
resource('ConfigMap','alisa-admin-config',data={'application-admin.yml':config},wave=-30)
api=registry+'alisa-admin-api@'+a.api_digest;web=registry+'alisa-admin-web@'+a.web_digest
secretEnv=[{'secretRef':{'name':n}} for n in ['alisa-jwt','alisa-user-db']]
base={'automountServiceAccountToken':False,'dnsPolicy':'Default','imagePullSecrets':[{'name':'alisa-admin-registry-auth'}],'restartPolicy':'Never','securityContext':{'runAsNonRoot':True,'runAsUser':1000,'runAsGroup':1000,'fsGroup':1000}}
container={'name':'migrate','image':api,'command':['java','-Dloader.main=com.alisa.user.tools.AuthSchemaMigration','-cp','/app/alisa-user.jar','org.springframework.boot.loader.PropertiesLauncher','apply'],'envFrom':secretEnv,'env':[{'name':'AUTH_BACKUP_KEY','valueFrom':{'secretKeyRef':{'name':'alisa-admin-bootstrap','key':'AUTH_BACKUP_KEY'}}},{'name':'AUTH_BACKUP_DIRECTORY','value':'/backups'},{'name':'JAVA_TOOL_OPTIONS','value':'-XX:MaxRAMPercentage=65'}],'volumeMounts':[{'name':'backups','mountPath':'/backups'},{'name':'tmp','mountPath':'/tmp'}],'resources':{'requests':{'cpu':'100m','memory':'128Mi'},'limits':{'cpu':'1','memory':'512Mi'}},'securityContext':{'allowPrivilegeEscalation':False,'readOnlyRootFilesystem':True,'capabilities':{'drop':['ALL']}}}
pod=dict(base,containers=[container],volumes=[{'name':'backups','persistentVolumeClaim':{'claimName':'alisa-admin-auth-backups'}},{'name':'tmp','emptyDir':{}}])
resource('Job','alisa-admin-auth-schema-'+a.api_digest[7:19],{'backoffLimit':0,'activeDeadlineSeconds':900,'template':{'spec':pod}},wave=-20)
for comp,image,port,uid,wave in [('api',api,8084,1000,0),('web',web,8080,101,10)]:
 name='alisa-admin-'+comp;labels={'app.kubernetes.io/name':name}
 health='/api/v1/user/health' if comp=='api' else '/healthz'
 con={'name':comp,'image':image,'ports':[{'name':'http','containerPort':port}],'securityContext':{'allowPrivilegeEscalation':False,'readOnlyRootFilesystem':True,'capabilities':{'drop':['ALL']}},'volumeMounts':[{'name':'tmp','mountPath':'/tmp'}],'resources':{'requests':{'cpu':'100m','memory':'256Mi' if comp=='api' else '32Mi'},'limits':{'cpu':'1','memory':'768Mi' if comp=='api' else '128Mi'}},'readinessProbe':{'httpGet':{'path':health,'port':'http'},'periodSeconds':10},'livenessProbe':{'httpGet':{'path':health,'port':'http'},'periodSeconds':20},'startupProbe':{'httpGet':{'path':health,'port':'http'},'periodSeconds':5,'failureThreshold':60}}
 volumes=[{'name':'tmp','emptyDir':{}}]
 if comp=='api':
  con['envFrom'] = [*secretEnv, {'secretRef': {'name': 'alisa-admin-account-delivery', 'optional': True}}]
  con['env']=[{'name':'SPRING_PROFILES_ACTIVE','value':'admin'},{'name':'SPRING_CONFIG_ADDITIONAL_LOCATION','value':'file:/config/'},{'name':'JAVA_TOOL_OPTIONS','value':'-XX:MaxRAMPercentage=65'},{'name':'PORTAL_BOOTSTRAP_ADMINISTRATOR','valueFrom':{'secretKeyRef':{'name':'alisa-admin-bootstrap','key':'PORTAL_BOOTSTRAP_ADMINISTRATOR'}}}]
  con['volumeMounts'].append({'name':'config','mountPath':'/config','readOnly':True});volumes.append({'name':'config','configMap':{'name':'alisa-admin-config'}})
 else:con['command']=['nginx','-g','daemon off;']
 pod={'automountServiceAccountToken':False,'dnsPolicy':'ClusterFirst' if comp=='web' else 'Default','imagePullSecrets':[{'name':'alisa-admin-registry-auth'}],'securityContext':{'runAsNonRoot':True,'runAsUser':uid,'runAsGroup':uid,'fsGroup':uid},'containers':[con],'volumes':volumes}
 resource('Deployment',name,{'replicas':1,'strategy':{'type':'Recreate'},'selector':{'matchLabels':labels},'template':{'metadata':{'labels':labels},'spec':pod}},wave=wave)
 resource('Service',name,{'selector':labels,'ports':[{'name':'http','port':port,'targetPort':'http'}]},wave=wave)
resource('HTTPRoute','alisa-admin',{'parentRefs':[{'name':'traefik-gateway','namespace':'traefik'}],'hostnames':['alisa.vip.cpolar.cn'],'rules':[{'matches':[{'path':{'type':'Exact','value':'/admin'}}],'filters':[{'type':'RequestRedirect','requestRedirect':{'path':{'type':'ReplaceFullPath','replaceFullPath':'/admin/'},'statusCode':302}}]},{'matches':[{'path':{'type':'PathPrefix','value':'/admin'}}],'filters':[{'type':'URLRewrite','urlRewrite':{'path':{'type':'ReplacePrefixMatch','replacePrefixMatch':'/'}}}],'backendRefs':[{'name':'alisa-admin-web','port':8080}]}]},wave=20)
a.output.mkdir(parents=True,exist_ok=True)
files=[]
for item in items:
 filename=item['kind'].lower()+'-'+item['metadata']['name']+'.json'
 (a.output/filename).write_text(json.dumps(item,indent=2,ensure_ascii=False)+'\n')
 files.append(filename)
(a.output/'kustomization.yaml').write_text('apiVersion: kustomize.config.k8s.io/v1beta1\nkind: Kustomization\nresources:\n'+''.join('  - '+f+'\n' for f in files))
print('Rendered',len(items),'resources with immutable internal images')
