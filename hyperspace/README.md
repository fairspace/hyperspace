# A Helm chart for VRE hyperspace
This helm chart will install and setup a VRE hyperspace, including keycloak. 

Contains:
- (optional) Ingress NGINX controller
- Keycloak

## Prerequisites
This chart relies on the following prerequisites:
- If ingress is enabled, an active ingress controller should be present in the cluster. If this has not been setup, you can 
  let this chart handle the installation by setting `hyperspace.ingress.controller.required` to true.
- If a TLS certificate is to be obtained automatically, an installation of `cert-manager` should be present in the cluster. See
  https://cert-manager.readthedocs.io/en/latest/getting-started/2-installing.html#with-helm for the easiest way to set it up. Please
  note that the certificate that is being created, relies on a dns01 challenge provider being configured properly, as well as on a 
  certificate issuer being setup. See the [cert-manager docs](https://cert-manager.readthedocs.io) for more information.

## How to install

```
helm repo add chartmuseum https://chartmuseum.jx.test.fairdev.app/
helm repo update
helm install --name=hyperspace chartmuseum/hyperspace --namespace=hyperspace -f config.yaml
```

## Install on minikube
```
helm repo add chartmuseum https://chartmuseum.jx.test.fairdev.app/
helm repo update
helm install --name=hyperspace chartmuseum/hyperspace --namespace=hyperspace --set hyperspace.ingress.enabled=false  --set keycloak.keycloak.service.type=NodePort
```

To retrieve the port that keycloak is running on, run:
`kubectl get svc hyperspace-keycloak-http --namespace=hyperspace  -o jsonpath="{.spec.ports[0].nodePort}"`

## Configuration
Use `helm ... -f config.yaml` to override default configuration parameters from `values.yaml`.


| Parameter  | Description  | Default |
|---|---|---|
| `keycloak.keycloak.service.type`  | [Servicetype](https://kubernetes.io/docs/concepts/services-networking/service/#publishing-services-service-types) for the Keycloak service. |  ClusterIP |
| `ingress.enabled`  | Whether or not an ingress is setup for the hyperspace components. Should be set to false when running locally.  | true  |
| `ingress.domain`   | Domain that is used for setting up the hyperspace. Is used as postfix for the hostname for the specific components. For example setting `fairspace.app` as domain will setup keycloak at `keycloak.fairspace.app`  | hyperspace.ci.test.fairdev.app  |
| `ingress.tls.enabled`  | Whether or not an TLS is enabled on the ingresses for hyperspace  | true  |
| `ingress.tls.secretNameOverride`  | If set, this secret name is used for loading certificates for TLS. | `tls-<release name>` |
| `ingress.tls.certificate.obtain`  | If set, a `Certificate` object will be created, such that [cert-manager](https://cert-manager.readthedocs.io/en/latest/) will request a certificate automatically. | true |
| `ingress.controller.required`  | If set to true, an nginx-ingress controller will be installed along with the application. | false |

To retrieve the initial user password run:

`kubectl get secret --namespace hyperspace hyperspace-keycloak-http -o jsonpath="{.data.password}" | base64 --decode; echo`


