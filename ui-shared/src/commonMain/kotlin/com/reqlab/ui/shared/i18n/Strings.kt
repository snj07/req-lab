package com.reqlab.ui.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Supported application languages.
 */
enum class AppLanguage(val code: String, val displayName: String) {
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
}

/**
 * Composable-accessible accessor for translation strings.
 * Usage: `Strings.appName`, `Strings.send`, etc.
 */
object Strings {
    // ── General ─────────────────────────────────
    val appName: String @Composable @ReadOnlyComposable get() = t("app_name")
    val send: String @Composable @ReadOnlyComposable get() = t("send")
    val cancel: String @Composable @ReadOnlyComposable get() = t("cancel")
    val save: String @Composable @ReadOnlyComposable get() = t("save")
    val delete: String @Composable @ReadOnlyComposable get() = t("delete")
    val close: String @Composable @ReadOnlyComposable get() = t("close")
    val confirm: String @Composable @ReadOnlyComposable get() = t("confirm")
    val add: String @Composable @ReadOnlyComposable get() = t("add")
    val edit: String @Composable @ReadOnlyComposable get() = t("edit")
    val search: String @Composable @ReadOnlyComposable get() = t("search")
    val copy: String @Composable @ReadOnlyComposable get() = t("copy")
    val download: String @Composable @ReadOnlyComposable get() = t("download")
    val loading: String @Composable @ReadOnlyComposable get() = t("loading")
    val error: String @Composable @ReadOnlyComposable get() = t("error")
    val success: String @Composable @ReadOnlyComposable get() = t("success")
    val retry: String @Composable @ReadOnlyComposable get() = t("retry")

    // ── Sidebar ─────────────────────────────────
    val history: String @Composable @ReadOnlyComposable get() = t("history")
    val collections: String @Composable @ReadOnlyComposable get() = t("collections")
    val environments: String @Composable @ReadOnlyComposable get() = t("environments")
    val searchRequests: String @Composable @ReadOnlyComposable get() = t("search_requests")
    val noHistory: String @Composable @ReadOnlyComposable get() = t("no_history")
    val clearHistory: String @Composable @ReadOnlyComposable get() = t("clear_history")
    val newRequest: String @Composable @ReadOnlyComposable get() = t("new_request")
    val newFolder: String @Composable @ReadOnlyComposable get() = t("new_folder")
    val collapseAll: String @Composable @ReadOnlyComposable get() = t("collapse_all")
    val expandAll: String @Composable @ReadOnlyComposable get() = t("expand_all")
    val noEnvironmentsConfigured: String @Composable @ReadOnlyComposable get() = t("no_environments_configured")
    val createEnvironment: String @Composable @ReadOnlyComposable get() = t("create_environment")
    val noRequestSelected: String @Composable @ReadOnlyComposable get() = t("no_request_selected")
    val openRequestToStart: String @Composable @ReadOnlyComposable get() = t("open_request_to_start")

    // ── Request Editor ──────────────────────────
    val params: String @Composable @ReadOnlyComposable get() = t("params")
    val headers: String @Composable @ReadOnlyComposable get() = t("headers")
    val body: String @Composable @ReadOnlyComposable get() = t("body")
    val auth: String @Composable @ReadOnlyComposable get() = t("auth")
    val preRequest: String @Composable @ReadOnlyComposable get() = t("pre_request")
    val tests: String @Composable @ReadOnlyComposable get() = t("tests")
    val sendRequest: String @Composable @ReadOnlyComposable get() = t("send_request")
    val enterUrl: String @Composable @ReadOnlyComposable get() = t("enter_url")
    val urlIsEmpty: String @Composable @ReadOnlyComposable get() = t("url_is_empty")

    // ── Response Viewer ─────────────────────────
    val response: String @Composable @ReadOnlyComposable get() = t("response")
    val responseBody: String @Composable @ReadOnlyComposable get() = t("response_body")
    val cookies: String @Composable @ReadOnlyComposable get() = t("cookies")
    val timing: String @Composable @ReadOnlyComposable get() = t("timing")
    val raw: String @Composable @ReadOnlyComposable get() = t("raw")
    val noCookies: String @Composable @ReadOnlyComposable get() = t("no_cookies")
    val sendToSeeResponse: String @Composable @ReadOnlyComposable get() = t("send_to_see_response")
    val sendingRequest: String @Composable @ReadOnlyComposable get() = t("sending_request")
    val requestFailed: String @Composable @ReadOnlyComposable get() = t("request_failed")
    val format: String @Composable @ReadOnlyComposable get() = t("format")
    val wordWrap: String @Composable @ReadOnlyComposable get() = t("word_wrap")
    val searchInResponse: String @Composable @ReadOnlyComposable get() = t("search_in_response")
    val copyBody: String @Composable @ReadOnlyComposable get() = t("copy_body")
    val downloadResponse: String @Composable @ReadOnlyComposable get() = t("download_response")
    val noResults: String @Composable @ReadOnlyComposable get() = t("no_results")

    // ── Timing View ─────────────────────────────
    val requestTimingBreakdown: String @Composable @ReadOnlyComposable get() = t("request_timing_breakdown")
    val dnsLookup: String @Composable @ReadOnlyComposable get() = t("dns_lookup")
    val tcpConnect: String @Composable @ReadOnlyComposable get() = t("tcp_connect")
    val tlsHandshake: String @Composable @ReadOnlyComposable get() = t("tls_handshake")
    val serverProcessing: String @Composable @ReadOnlyComposable get() = t("server_processing")
    val contentDownload: String @Composable @ReadOnlyComposable get() = t("content_download")
    val timingNotAvailable: String @Composable @ReadOnlyComposable get() = t("timing_not_available")

    // ── Settings ────────────────────────────────
    val settings: String @Composable @ReadOnlyComposable get() = t("settings")
    val general: String @Composable @ReadOnlyComposable get() = t("general")
    val theme: String @Composable @ReadOnlyComposable get() = t("theme")
    val network: String @Composable @ReadOnlyComposable get() = t("network")
    val proxy: String @Composable @ReadOnlyComposable get() = t("proxy")
    val language: String @Composable @ReadOnlyComposable get() = t("language")
    val autoSave: String @Composable @ReadOnlyComposable get() = t("auto_save")
    val confirmBeforeDelete: String @Composable @ReadOnlyComposable get() = t("confirm_before_delete")
    val defaultTimeout: String @Composable @ReadOnlyComposable get() = t("default_timeout")
    val responseLayout: String @Composable @ReadOnlyComposable get() = t("response_layout")
    val followRedirects: String @Composable @ReadOnlyComposable get() = t("follow_redirects")
    val darkMode: String @Composable @ReadOnlyComposable get() = t("dark_mode")
    val lightMode: String @Composable @ReadOnlyComposable get() = t("light_mode")
    val systemTheme: String @Composable @ReadOnlyComposable get() = t("system_theme")

    // ── Global Variables ────────────────────────
    val globalVariables: String @Composable @ReadOnlyComposable get() = t("global_variables")
    val globalVariablesDesc: String @Composable @ReadOnlyComposable get() = t("global_variables_desc")
    val addVariable: String @Composable @ReadOnlyComposable get() = t("add_variable")
    val noGlobalVariables: String @Composable @ReadOnlyComposable get() = t("no_global_variables")
    val variableName: String @Composable @ReadOnlyComposable get() = t("variable_name")
    val value: String @Composable @ReadOnlyComposable get() = t("value")

    // ── Realtime ────────────────────────────────
    val connect: String @Composable @ReadOnlyComposable get() = t("connect")
    val disconnect: String @Composable @ReadOnlyComposable get() = t("disconnect")
    val connected: String @Composable @ReadOnlyComposable get() = t("connected")
    val disconnected: String @Composable @ReadOnlyComposable get() = t("disconnected")
    val sendMessage: String @Composable @ReadOnlyComposable get() = t("send_message")
    val messageHistory: String @Composable @ReadOnlyComposable get() = t("message_history")
    val protocols: String @Composable @ReadOnlyComposable get() = t("protocols")
    val communication: String @Composable @ReadOnlyComposable get() = t("communication")

    // ── GraphQL ─────────────────────────────────
    val query: String @Composable @ReadOnlyComposable get() = t("query")
    val variables: String @Composable @ReadOnlyComposable get() = t("variables")
    val schemaExplorer: String @Composable @ReadOnlyComposable get() = t("schema_explorer")
    val runQuery: String @Composable @ReadOnlyComposable get() = t("run_query")
    val introspect: String @Composable @ReadOnlyComposable get() = t("introspect")

    // ── Bottom Panel ────────────────────────────
    val console: String @Composable @ReadOnlyComposable get() = t("console")
    val testResults: String @Composable @ReadOnlyComposable get() = t("test_results")
    val logs: String @Composable @ReadOnlyComposable get() = t("logs")

    // ── Import / Export ─────────────────────────
    val importCollection: String @Composable @ReadOnlyComposable get() = t("import_collection")
    val exportCollection: String @Composable @ReadOnlyComposable get() = t("export_collection")
    val importSuccess: String @Composable @ReadOnlyComposable get() = t("import_success")
    val exportSuccess: String @Composable @ReadOnlyComposable get() = t("export_success")
    val operationFailed: String @Composable @ReadOnlyComposable get() = t("operation_failed")

    @Composable
    @ReadOnlyComposable
    private fun t(key: String): String = LocalI18n.current.get(key)
}

/**
 * Translation provider that holds all translation maps and resolves keys.
 */
class I18nProvider(private val language: AppLanguage = AppLanguage.EN) {

    private val translations: Map<String, String> = when (language) {
        AppLanguage.EN -> enTranslations
        AppLanguage.ES -> esTranslations
        AppLanguage.FR -> frTranslations
        AppLanguage.DE -> deTranslations
    }

    private val fallback: Map<String, String> = enTranslations

    fun get(key: String): String = translations[key] ?: fallback[key] ?: key
}

/**
 * CompositionLocal for the active i18n provider.
 */
val LocalI18n = compositionLocalOf { I18nProvider() }

// ── Translation Maps ────────────────────────────────────────────

private val enTranslations = mapOf(
    // General
    "app_name" to "ReqLab",
    "send" to "Send",
    "cancel" to "Cancel",
    "save" to "Save",
    "delete" to "Delete",
    "close" to "Close",
    "confirm" to "Confirm",
    "add" to "Add",
    "edit" to "Edit",
    "search" to "Search",
    "copy" to "Copy",
    "download" to "Download",
    "loading" to "Loading…",
    "error" to "Error",
    "success" to "Success",
    "retry" to "Retry",
    // Sidebar
    "history" to "History",
    "collections" to "Collections",
    "environments" to "Environments",
    "search_requests" to "Search requests…",
    "no_history" to "No history yet",
    "clear_history" to "Clear history",
    "new_request" to "New Request",
    "new_folder" to "New Folder",
    "collapse_all" to "Collapse All",
    "expand_all" to "Expand All",
    "no_environments_configured" to "No environments configured",
    "create_environment" to "Create Environment",
    "no_request_selected" to "No request selected",
    "open_request_to_start" to "Open a request from History or Collections to start.",
    // Request Editor
    "params" to "Params",
    "headers" to "Headers",
    "body" to "Body",
    "auth" to "Auth",
    "pre_request" to "Pre-request",
    "tests" to "Tests",
    "send_request" to "Send Request",
    "enter_url" to "Enter URL",
    "url_is_empty" to "URL is empty",
    // Response
    "response" to "Response",
    "response_body" to "Body",
    "cookies" to "Cookies",
    "timing" to "Timing",
    "raw" to "Raw",
    "no_cookies" to "No cookies",
    "send_to_see_response" to "Enter a URL and click Send to see the response here",
    "sending_request" to "Sending request…",
    "request_failed" to "Request failed",
    "format" to "Format",
    "word_wrap" to "Word wrap",
    "search_in_response" to "Search in response…",
    "copy_body" to "Copy body",
    "download_response" to "Download response",
    "no_results" to "0 results",
    // Timing
    "request_timing_breakdown" to "Request Timing Breakdown",
    "dns_lookup" to "DNS Lookup",
    "tcp_connect" to "TCP Connect",
    "tls_handshake" to "TLS Handshake",
    "server_processing" to "Server Processing",
    "content_download" to "Content Download",
    "timing_not_available" to "Detailed timing phases are not available for this response.",
    // Settings
    "settings" to "Settings",
    "general" to "General",
    "theme" to "Theme",
    "network" to "Network",
    "proxy" to "Proxy",
    "language" to "Language",
    "auto_save" to "Auto-save requests",
    "confirm_before_delete" to "Confirm before delete",
    "default_timeout" to "Default timeout (seconds)",
    "response_layout" to "Response layout",
    "follow_redirects" to "Follow redirects",
    "dark_mode" to "Dark",
    "light_mode" to "Light",
    "system_theme" to "System",
    // Global Variables
    "global_variables" to "Global Variables",
    "global_variables_desc" to "Global variables are available in all environments and requests. Use {{variableName}} syntax. Environment variables override globals.",
    "add_variable" to "Add Variable",
    "no_global_variables" to "No global variables defined",
    "variable_name" to "Variable name",
    "value" to "Value",
    // Realtime
    "connect" to "Connect",
    "disconnect" to "Disconnect",
    "connected" to "Connected",
    "disconnected" to "Disconnected",
    "send_message" to "Send Message",
    "message_history" to "Message History",
    "protocols" to "Protocols",
    "communication" to "Communication",
    // GraphQL
    "query" to "Query",
    "variables" to "Variables",
    "schema_explorer" to "Schema Explorer",
    "run_query" to "Run Query",
    "introspect" to "Introspect",
    // Bottom Panel
    "console" to "Console",
    "test_results" to "Test Results",
    "logs" to "Logs",
    // Import / Export
    "import_collection" to "Import Collection",
    "export_collection" to "Export Collection",
    "import_success" to "Collection imported successfully",
    "export_success" to "Collection exported successfully",
    "operation_failed" to "Operation failed",
)

private val esTranslations = mapOf(
    "app_name" to "ReqLab",
    "send" to "Enviar",
    "cancel" to "Cancelar",
    "save" to "Guardar",
    "delete" to "Eliminar",
    "close" to "Cerrar",
    "confirm" to "Confirmar",
    "add" to "Añadir",
    "edit" to "Editar",
    "search" to "Buscar",
    "copy" to "Copiar",
    "download" to "Descargar",
    "loading" to "Cargando…",
    "error" to "Error",
    "success" to "Éxito",
    "retry" to "Reintentar",
    "history" to "Historial",
    "collections" to "Colecciones",
    "environments" to "Entornos",
    "search_requests" to "Buscar solicitudes…",
    "no_history" to "Sin historial aún",
    "clear_history" to "Limpiar historial",
    "new_request" to "Nueva solicitud",
    "new_folder" to "Nueva carpeta",
    "collapse_all" to "Contraer todo",
    "expand_all" to "Expandir todo",
    "no_environments_configured" to "No hay entornos configurados",
    "create_environment" to "Crear entorno",
    "no_request_selected" to "No hay solicitud seleccionada",
    "open_request_to_start" to "Abra una solicitud desde Historial o Colecciones para comenzar.",
    "params" to "Parámetros",
    "headers" to "Encabezados",
    "body" to "Cuerpo",
    "auth" to "Autenticación",
    "pre_request" to "Pre-solicitud",
    "tests" to "Pruebas",
    "send_request" to "Enviar solicitud",
    "enter_url" to "Introducir URL",
    "url_is_empty" to "La URL está vacía",
    "response" to "Respuesta",
    "response_body" to "Cuerpo",
    "cookies" to "Cookies",
    "timing" to "Tiempos",
    "raw" to "Sin formato",
    "no_cookies" to "Sin cookies",
    "send_to_see_response" to "Introduce una URL y haz clic en Enviar para ver la respuesta aquí",
    "sending_request" to "Enviando solicitud…",
    "request_failed" to "Solicitud fallida",
    "format" to "Formatear",
    "word_wrap" to "Ajuste de línea",
    "search_in_response" to "Buscar en la respuesta…",
    "copy_body" to "Copiar cuerpo",
    "download_response" to "Descargar respuesta",
    "no_results" to "0 resultados",
    "request_timing_breakdown" to "Desglose de tiempos de la solicitud",
    "dns_lookup" to "Búsqueda DNS",
    "tcp_connect" to "Conexión TCP",
    "tls_handshake" to "Negociación TLS",
    "server_processing" to "Procesamiento del servidor",
    "content_download" to "Descarga de contenido",
    "timing_not_available" to "Las fases de tiempo detalladas no están disponibles para esta respuesta.",
    "settings" to "Configuración",
    "general" to "General",
    "theme" to "Tema",
    "network" to "Red",
    "proxy" to "Proxy",
    "language" to "Idioma",
    "auto_save" to "Auto-guardar solicitudes",
    "confirm_before_delete" to "Confirmar antes de eliminar",
    "default_timeout" to "Tiempo de espera predeterminado (segundos)",
    "response_layout" to "Diseño de respuesta",
    "follow_redirects" to "Seguir redirecciones",
    "dark_mode" to "Oscuro",
    "light_mode" to "Claro",
    "system_theme" to "Sistema",
    "global_variables" to "Variables globales",
    "global_variables_desc" to "Las variables globales están disponibles en todos los entornos y solicitudes. Usa la sintaxis {{nombreVariable}}. Las variables de entorno tienen prioridad sobre las globales.",
    "add_variable" to "Añadir variable",
    "no_global_variables" to "No hay variables globales definidas",
    "variable_name" to "Nombre de variable",
    "value" to "Valor",
    "connect" to "Conectar",
    "disconnect" to "Desconectar",
    "connected" to "Conectado",
    "disconnected" to "Desconectado",
    "send_message" to "Enviar mensaje",
    "message_history" to "Historial de mensajes",
    "protocols" to "Protocolos",
    "communication" to "Comunicación",
    "query" to "Consulta",
    "variables" to "Variables",
    "schema_explorer" to "Explorador de esquema",
    "run_query" to "Ejecutar consulta",
    "introspect" to "Introspección",
    "console" to "Consola",
    "test_results" to "Resultados de pruebas",
    "logs" to "Registros",
    "import_collection" to "Importar colección",
    "export_collection" to "Exportar colección",
    "import_success" to "Colección importada exitosamente",
    "export_success" to "Colección exportada exitosamente",
    "operation_failed" to "Operación fallida",
)

private val frTranslations = mapOf(
    "app_name" to "ReqLab",
    "send" to "Envoyer",
    "cancel" to "Annuler",
    "save" to "Sauvegarder",
    "delete" to "Supprimer",
    "close" to "Fermer",
    "confirm" to "Confirmer",
    "add" to "Ajouter",
    "edit" to "Modifier",
    "search" to "Rechercher",
    "copy" to "Copier",
    "download" to "Télécharger",
    "loading" to "Chargement…",
    "error" to "Erreur",
    "success" to "Succès",
    "retry" to "Réessayer",
    "history" to "Historique",
    "collections" to "Collections",
    "environments" to "Environnements",
    "search_requests" to "Rechercher des requêtes…",
    "no_history" to "Pas encore d'historique",
    "clear_history" to "Effacer l'historique",
    "new_request" to "Nouvelle requête",
    "new_folder" to "Nouveau dossier",
    "collapse_all" to "Tout réduire",
    "expand_all" to "Tout développer",
    "no_environments_configured" to "Aucun environnement configuré",
    "create_environment" to "Créer un environnement",
    "no_request_selected" to "Aucune requête sélectionnée",
    "open_request_to_start" to "Ouvrez une requête depuis l'Historique ou les Collections pour commencer.",
    "params" to "Paramètres",
    "headers" to "En-têtes",
    "body" to "Corps",
    "auth" to "Authentification",
    "pre_request" to "Pré-requête",
    "tests" to "Tests",
    "send_request" to "Envoyer la requête",
    "enter_url" to "Entrer l'URL",
    "url_is_empty" to "L'URL est vide",
    "response" to "Réponse",
    "response_body" to "Corps",
    "cookies" to "Cookies",
    "timing" to "Chronométrage",
    "raw" to "Brut",
    "no_cookies" to "Pas de cookies",
    "send_to_see_response" to "Entrez une URL et cliquez sur Envoyer pour voir la réponse ici",
    "sending_request" to "Envoi de la requête…",
    "request_failed" to "Requête échouée",
    "format" to "Formater",
    "word_wrap" to "Retour à la ligne",
    "search_in_response" to "Rechercher dans la réponse…",
    "copy_body" to "Copier le corps",
    "download_response" to "Télécharger la réponse",
    "no_results" to "0 résultats",
    "request_timing_breakdown" to "Détail du chronométrage de la requête",
    "dns_lookup" to "Recherche DNS",
    "tcp_connect" to "Connexion TCP",
    "tls_handshake" to "Négociation TLS",
    "server_processing" to "Traitement serveur",
    "content_download" to "Téléchargement du contenu",
    "timing_not_available" to "Les phases de chronométrage détaillées ne sont pas disponibles pour cette réponse.",
    "settings" to "Paramètres",
    "general" to "Général",
    "theme" to "Thème",
    "network" to "Réseau",
    "proxy" to "Proxy",
    "language" to "Langue",
    "auto_save" to "Sauvegarde automatique",
    "confirm_before_delete" to "Confirmer avant de supprimer",
    "default_timeout" to "Délai d'attente par défaut (secondes)",
    "response_layout" to "Disposition de la réponse",
    "follow_redirects" to "Suivre les redirections",
    "dark_mode" to "Sombre",
    "light_mode" to "Clair",
    "system_theme" to "Système",
    "global_variables" to "Variables globales",
    "global_variables_desc" to "Les variables globales sont disponibles dans tous les environnements et requêtes. Utilisez la syntaxe {{nomVariable}}. Les variables d'environnement ont priorité sur les globales.",
    "add_variable" to "Ajouter une variable",
    "no_global_variables" to "Aucune variable globale définie",
    "variable_name" to "Nom de variable",
    "value" to "Valeur",
    "connect" to "Connecter",
    "disconnect" to "Déconnecter",
    "connected" to "Connecté",
    "disconnected" to "Déconnecté",
    "send_message" to "Envoyer un message",
    "message_history" to "Historique des messages",
    "protocols" to "Protocoles",
    "communication" to "Communication",
    "query" to "Requête",
    "variables" to "Variables",
    "schema_explorer" to "Explorateur de schéma",
    "run_query" to "Exécuter la requête",
    "introspect" to "Introspection",
    "console" to "Console",
    "test_results" to "Résultats des tests",
    "logs" to "Journaux",
    "import_collection" to "Importer une collection",
    "export_collection" to "Exporter une collection",
    "import_success" to "Collection importée avec succès",
    "export_success" to "Collection exportée avec succès",
    "operation_failed" to "Opération échouée",
)

private val deTranslations = mapOf(
    "app_name" to "ReqLab",
    "send" to "Senden",
    "cancel" to "Abbrechen",
    "save" to "Speichern",
    "delete" to "Löschen",
    "close" to "Schließen",
    "confirm" to "Bestätigen",
    "add" to "Hinzufügen",
    "edit" to "Bearbeiten",
    "search" to "Suchen",
    "copy" to "Kopieren",
    "download" to "Herunterladen",
    "loading" to "Laden…",
    "error" to "Fehler",
    "success" to "Erfolg",
    "retry" to "Wiederholen",
    "history" to "Verlauf",
    "collections" to "Sammlungen",
    "environments" to "Umgebungen",
    "search_requests" to "Anfragen suchen…",
    "no_history" to "Noch kein Verlauf",
    "clear_history" to "Verlauf löschen",
    "new_request" to "Neue Anfrage",
    "new_folder" to "Neuer Ordner",
    "collapse_all" to "Alle einklappen",
    "expand_all" to "Alle ausklappen",
    "no_environments_configured" to "Keine Umgebungen konfiguriert",
    "create_environment" to "Umgebung erstellen",
    "no_request_selected" to "Keine Anfrage ausgewählt",
    "open_request_to_start" to "Öffnen Sie eine Anfrage aus Verlauf oder Sammlungen, um zu beginnen.",
    "params" to "Parameter",
    "headers" to "Header",
    "body" to "Body",
    "auth" to "Authentifizierung",
    "pre_request" to "Vor-Anfrage",
    "tests" to "Tests",
    "send_request" to "Anfrage senden",
    "enter_url" to "URL eingeben",
    "url_is_empty" to "URL ist leer",
    "response" to "Antwort",
    "response_body" to "Body",
    "cookies" to "Cookies",
    "timing" to "Zeitmessung",
    "raw" to "Roh",
    "no_cookies" to "Keine Cookies",
    "send_to_see_response" to "Geben Sie eine URL ein und klicken Sie auf Senden, um die Antwort hier zu sehen",
    "sending_request" to "Anfrage wird gesendet…",
    "request_failed" to "Anfrage fehlgeschlagen",
    "format" to "Formatieren",
    "word_wrap" to "Zeilenumbruch",
    "search_in_response" to "In Antwort suchen…",
    "copy_body" to "Body kopieren",
    "download_response" to "Antwort herunterladen",
    "no_results" to "0 Ergebnisse",
    "request_timing_breakdown" to "Aufschlüsselung der Anfragezeit",
    "dns_lookup" to "DNS-Auflösung",
    "tcp_connect" to "TCP-Verbindung",
    "tls_handshake" to "TLS-Handshake",
    "server_processing" to "Serververarbeitung",
    "content_download" to "Inhaltsdownload",
    "timing_not_available" to "Detaillierte Zeitphasen sind für diese Antwort nicht verfügbar.",
    "settings" to "Einstellungen",
    "general" to "Allgemein",
    "theme" to "Design",
    "network" to "Netzwerk",
    "proxy" to "Proxy",
    "language" to "Sprache",
    "auto_save" to "Automatisch speichern",
    "confirm_before_delete" to "Vor dem Löschen bestätigen",
    "default_timeout" to "Standard-Zeitlimit (Sekunden)",
    "response_layout" to "Antwort-Layout",
    "follow_redirects" to "Weiterleitungen folgen",
    "dark_mode" to "Dunkel",
    "light_mode" to "Hell",
    "system_theme" to "System",
    "global_variables" to "Globale Variablen",
    "global_variables_desc" to "Globale Variablen sind in allen Umgebungen und Anfragen verfügbar. Verwenden Sie die Syntax {{variablenName}}. Umgebungsvariablen haben Vorrang vor globalen Variablen.",
    "add_variable" to "Variable hinzufügen",
    "no_global_variables" to "Keine globalen Variablen definiert",
    "variable_name" to "Variablenname",
    "value" to "Wert",
    "connect" to "Verbinden",
    "disconnect" to "Trennen",
    "connected" to "Verbunden",
    "disconnected" to "Getrennt",
    "send_message" to "Nachricht senden",
    "message_history" to "Nachrichtenverlauf",
    "protocols" to "Protokolle",
    "communication" to "Kommunikation",
    "query" to "Abfrage",
    "variables" to "Variablen",
    "schema_explorer" to "Schema-Explorer",
    "run_query" to "Abfrage ausführen",
    "introspect" to "Introspektion",
    "console" to "Konsole",
    "test_results" to "Testergebnisse",
    "logs" to "Protokolle",
    "import_collection" to "Sammlung importieren",
    "export_collection" to "Sammlung exportieren",
    "import_success" to "Sammlung erfolgreich importiert",
    "export_success" to "Sammlung erfolgreich exportiert",
    "operation_failed" to "Vorgang fehlgeschlagen",
)
