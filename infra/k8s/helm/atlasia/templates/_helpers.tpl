{{/*
Expand the name of the chart.
*/}}
{{- define "atlasia.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "atlasia.fullname" -}}
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
{{- define "atlasia.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "atlasia.labels" -}}
helm.sh/chart: {{ include "atlasia.chart" . }}
{{ include "atlasia.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "atlasia.selectorLabels" -}}
app.kubernetes.io/name: {{ include "atlasia.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Orchestrator labels
*/}}
{{- define "atlasia.orchestrator.labels" -}}
{{ include "atlasia.labels" . }}
app.kubernetes.io/component: orchestrator
{{- end }}

{{/*
Orchestrator selector labels
*/}}
{{- define "atlasia.orchestrator.selectorLabels" -}}
{{ include "atlasia.selectorLabels" . }}
app.kubernetes.io/component: orchestrator
{{- end }}

{{/*
Frontend labels
*/}}
{{- define "atlasia.frontend.labels" -}}
{{ include "atlasia.labels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{/*
Frontend selector labels
*/}}
{{- define "atlasia.frontend.selectorLabels" -}}
{{ include "atlasia.selectorLabels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "atlasia.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "atlasia.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
PostgreSQL host
*/}}
{{- define "atlasia.postgresql.host" -}}
{{- if .Values.postgresql.enabled }}
{{- printf "%s-postgresql" (include "atlasia.fullname" .) }}
{{- else }}
{{- required "postgresql.externalHost is required when postgresql.enabled is false" .Values.postgresql.externalHost }}
{{- end }}
{{- end }}

{{/*
PostgreSQL port
*/}}
{{- define "atlasia.postgresql.port" -}}
{{- if .Values.postgresql.enabled }}
{{- .Values.postgresql.primary.service.ports.postgresql | default 5432 }}
{{- else }}
{{- .Values.postgresql.externalPort | default 5432 }}
{{- end }}
{{- end }}

{{/*
PostgreSQL database
*/}}
{{- define "atlasia.postgresql.database" -}}
{{- if .Values.postgresql.enabled }}
{{- .Values.postgresql.auth.database }}
{{- else }}
{{- .Values.postgresql.externalDatabase }}
{{- end }}
{{- end }}

{{/*
Secret name
*/}}
{{- define "atlasia.secretName" -}}
{{- if .Values.secrets.existingSecret }}
{{- .Values.secrets.existingSecret }}
{{- else }}
{{- include "atlasia.fullname" . }}-secrets
{{- end }}
{{- end }}
