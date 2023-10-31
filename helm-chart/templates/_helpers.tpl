{{/*
Expand the name of the chart.
*/}}
{{- define "enabler.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "enabler.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "enabler.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Cilium Multi-cluster global service annotations.
*/}}
{{- define "globalServiceAnnotations" -}}
io.cilium/global-service: "true"
io.cilium/service-affinity: remote
{{- end }}

{{/*
Name of the component autoconfig.
*/}}
{{- define "autoconfig.name" -}}
{{- printf "%s-autoconfig" (include "enabler.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified component autoconfig name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "autoconfig.fullname" -}}
{{- printf "%s-autoconfig" (include "enabler.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}


{{/*
Component autoconfig labels.
*/}}
{{- define "autoconfig.labels" -}}
helm.sh/chart: {{ include "enabler.chart" . }}
{{ include "autoconfig.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Component autoconfig selector labels.
*/}}
{{- define "autoconfig.selectorLabels" -}}
app.kubernetes.io/name: {{ include "enabler.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
enabler: {{ .Chart.Name }}
app.kubernetes.io/component: autoconfig
isMainInterface: "yes"
tier: {{ .Values.autoconfig.tier }}
{{- end }}

{{/*
Name of the component eventstoredb.
*/}}
{{- define "eventstoredb.name" -}}
{{- printf "%s-eventstoredb" (include "enabler.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified component eventstoredb name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "eventstoredb.fullname" -}}
{{- printf "%s-eventstoredb" (include "enabler.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create the default FQDN for eventstoredb headless service.
*/}}
{{- define "eventstoredb.svc.headless" -}}
{{- printf "%s-headless" (include "eventstoredb.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
EventStore Database hostname (inside Kubernetes).
*/}}
{{- define "eventstoredb.kubernetesServerName" -}}
{{- print (include "eventstoredb.fullname" .) ".default" }}
{{- end }}

{{/*
Component eventstoredb labels.
*/}}
{{- define "eventstoredb.labels" -}}
helm.sh/chart: {{ include "enabler.chart" . }}
{{ include "eventstoredb.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Component eventstoredb selector labels.
*/}}
{{- define "eventstoredb.selectorLabels" -}}
app.kubernetes.io/name: {{ include "enabler.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
enabler: {{ .Chart.Name }}
app.kubernetes.io/component: eventstoredb
isMainInterface: "no"
tier: {{ .Values.eventstoredb.tier }}
{{- end }}

{{/*
Name of the component apachezookeeper.
*/}}
{{- define "apachezookeeper.name" -}}
{{- printf "%s-apachezookeeper" (include "enabler.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified component apachezookeeper name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "apachezookeeper.fullname" -}}
{{- printf "%s-apachezookeeper" (include "enabler.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create the default FQDN for apachezookeeper headless service.
*/}}
{{- define "apachezookeeper.svc.headless" -}}
{{- printf "%s-headless" (include "apachezookeeper.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Apache Zookeeper hostname (inside Kubernetes).
*/}}
{{- define "apachezookeeper.kubernetesServerName" -}}
{{- print (include "apachezookeeper.fullname" .) ".default" }}
{{- end }}

{{/*
Component apachezookeeper labels.
*/}}
{{- define "apachezookeeper.labels" -}}
helm.sh/chart: {{ include "enabler.chart" . }}
{{ include "apachezookeeper.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Component apachezookeeper selector labels.
*/}}
{{- define "apachezookeeper.selectorLabels" -}}
app.kubernetes.io/name: {{ include "enabler.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
enabler: {{ .Chart.Name }}
app.kubernetes.io/component: apachezookeeper
isMainInterface: "no"
tier: {{ .Values.apachezookeeper.tier }}
{{- end }}

{{/*
Name of the component apachekafka.
*/}}
{{- define "apachekafka.name" -}}
{{- printf "%s-apachekafka" (include "enabler.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified component apachekafka name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "apachekafka.fullname" -}}
{{- printf "%s-apachekafka" (include "enabler.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create the default FQDN for apachekafka headless service.
*/}}
{{- define "apachekafka.svc.headless" -}}
{{- printf "%s-headless" (include "apachekafka.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Apache Kafka hostname (inside Kubernetes).
*/}}
{{- define "apachekafka.kubernetesServerName" -}}
{{- print (include "apachekafka.fullname" .) ".default" }}
{{- end }}

{{/*
Component apachekafka labels.
*/}}
{{- define "apachekafka.labels" -}}
helm.sh/chart: {{ include "enabler.chart" . }}
{{ include "apachekafka.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Component apachekafka selector labels.
*/}}
{{- define "apachekafka.selectorLabels" -}}
app.kubernetes.io/name: {{ include "enabler.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
enabler: {{ .Chart.Name }}
app.kubernetes.io/component: apachekafka
isMainInterface: "no"
tier: {{ .Values.apachekafka.tier }}
{{- end }}

{{/*
Name of the component apachekafkaui.
*/}}
{{- define "apachekafkaui.name" -}}
{{- printf "%s-apachekafkaui" (include "enabler.name" .) | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified component apachekafkaui name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "apachekafkaui.fullname" -}}
{{- printf "%s-apachekafkaui" (include "enabler.fullname" .) | trunc 63 | trimSuffix "-" }}
{{- end }}


{{/*
Component apachekafkaui labels.
*/}}
{{- define "apachekafkaui.labels" -}}
helm.sh/chart: {{ include "enabler.chart" . }}
{{ include "apachekafkaui.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Component apachekafkaui selector labels.
*/}}
{{- define "apachekafkaui.selectorLabels" -}}
app.kubernetes.io/name: {{ include "enabler.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
enabler: {{ .Chart.Name }}
app.kubernetes.io/component: apachekafkaui
isMainInterface: "no"
tier: {{ .Values.apachekafkaui.tier }}
{{- end }}





