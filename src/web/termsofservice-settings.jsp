<%@ page
    import="org.igniterealtime.openfire.plugin.termsofservice.TermsOfServicePlugin,
        org.igniterealtime.openfire.plugin.termsofservice.UrlUtil,
        org.jivesoftware.util.CookieUtils,
        org.jivesoftware.util.ParamUtils,
        org.jivesoftware.util.StringUtils,
        java.util.HashMap,
        java.util.Map"
    errorPage="error.jsp"%>
<%@ page import="org.igniterealtime.openfire.plugin.termsofservice.document.TosDocumentService" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
    webManager.init(pageContext);

    final TermsOfServicePlugin plugin = (TermsOfServicePlugin) XMPPServer.getInstance().getPluginManager().getPluginByName("Terms of Service").orElseThrow();
    final TosDocumentService documentService = plugin.getDocumentService();

    final boolean save = request.getParameter("save") != null;

    final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    final String csrfParam = ParamUtils.getParameter(request, "csrf");

    // Values stored in "errors" are i18n keys (resolved via <fmt:message> below), not literal text, so this page
    // stays translatable.
    final Map<String, String> errors = new HashMap<>();
    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "termsofservice.error.csrf");
        }

        final boolean enabled = ParamUtils.getBooleanParameter(request, "enabled", false);
        final String announcedProtocol = ParamUtils.getParameter(request, "announcedProtocol", true);
        final String announcedHost = ParamUtils.getParameter(request, "announcedHost", true);
        final String announcedPortRaw = ParamUtils.getParameter(request, "announcedPort", true);
        final String announcedContextRoot = ParamUtils.getParameter(request, "announcedContextRoot", true);

        Integer announcedPort = null;
        if (announcedPortRaw != null && !announcedPortRaw.isBlank()) {
            try {
                announcedPort = Integer.valueOf(announcedPortRaw.trim());
            } catch (final NumberFormatException e) {
                errors.put("announcedPort", "termsofservice.settings.error.announcedPort.notNumber");
            }
        }

        if (errors.isEmpty()) {
            TermsOfServicePlugin.ENABLED.setValue(enabled);
            TermsOfServicePlugin.ANNOUNCED_PROTOCOL.setValue(blankToNull(announcedProtocol));
            TermsOfServicePlugin.ANNOUNCED_HOST.setValue(blankToNull(announcedHost));
            TermsOfServicePlugin.ANNOUNCED_PORT.setValue(announcedPort);
            TermsOfServicePlugin.ANNOUNCED_CONTEXT_ROOT.setValue(blankToNull(announcedContextRoot));
            webManager.logEvent("updated terms of service settings", "enabled = " + enabled + "\nannounced protocol = " + announcedProtocol + "\nannounced host = " + announcedHost + "\nannounced port = " + announcedPort + "\nannounced context root = " + announcedContextRoot);
            response.sendRedirect("termsofservice-settings.jsp?settingsSaved=true");
            return;
        }
    }

    final String csrf = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrf, -1);
    pageContext.setAttribute("csrf", csrf);
    pageContext.setAttribute("errors", errors);

    pageContext.setAttribute("enabled", TermsOfServicePlugin.ENABLED.getValue());
    pageContext.setAttribute("hasCurrentDocument", documentService.getCurrent().isPresent());
    pageContext.setAttribute("announcedProtocol", TermsOfServicePlugin.ANNOUNCED_PROTOCOL.getValue());
    pageContext.setAttribute("announcedHost", TermsOfServicePlugin.ANNOUNCED_HOST.getValue());
    pageContext.setAttribute("announcedPort", TermsOfServicePlugin.ANNOUNCED_PORT.getValue());
    pageContext.setAttribute("announcedContextRoot", TermsOfServicePlugin.ANNOUNCED_CONTEXT_ROOT.getValue());
    pageContext.setAttribute("defaultPath", UrlUtil.DEFAULT_PATH);
    pageContext.setAttribute("effectiveUrl", UrlUtil.getAnnouncedTermsUrl());
%>
<%!
    // Small helper so an admin clearing a field back to blank stores "unset" rather than an empty string.
    private static String blankToNull(final String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
%>
<html>
<head>
    <title><fmt:message key="termsofservice.settings.title"/></title>
    <meta name="pageID" content="termsofservice-settings"/>
</head>
<body>

<c:if test="${param.settingsSaved eq 'true'}">
    <admin:infobox type="success"><fmt:message key="termsofservice.settings.saved"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['csrf']}">
    <admin:infobox type="error"><fmt:message key="${errors['csrf']}"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['announcedPort']}">
    <admin:infobox type="error">
        <fmt:message key="termsofservice.settings.error.announcedPort">
            <fmt:param><fmt:message key="${errors['announcedPort']}"/></fmt:param>
        </fmt:message>
    </admin:infobox>
</c:if>
<c:if test="${enabled and not hasCurrentDocument}">
    <admin:infobox type="warning">
        <fmt:message key="termsofservice.warning.noCurrentDocument"/>
    </admin:infobox>
</c:if>
<admin:infobox type="info">
    <fmt:message key="termsofservice.info.sasl2Only"/>
</admin:infobox>

<p>
    <fmt:message key="termsofservice.settings.intro1"/>
    <fmt:message key="termsofservice.settings.intro2"/>
    <a href="termsofservice-documents.jsp"><fmt:message key="termsofservice.documents.title"/></a>
    <fmt:message key="termsofservice.settings.intro3"/>
</p>

<form action="termsofservice-settings.jsp" method="post">
    <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>

    <fmt:message key="termsofservice.settings.enable.title" var="enableTitle"/>
    <admin:contentBox title="${admin:escapeHTMLTags(enableTitle)}">
        <p>
            <label>
                <input type="checkbox" name="enabled" value="true" ${enabled ? 'checked' : ''}/>
                <fmt:message key="termsofservice.settings.enable.checkbox"/>
            </label>
        </p>
        <p>
            <fmt:message key="termsofservice.settings.enable.help"/>
        </p>
    </admin:contentBox>

    <fmt:message key="termsofservice.settings.announce.title" var="announceTitle"/>
    <admin:contentBox title="${admin:escapeHTMLTags(announceTitle)}">
        <p>
            <fmt:message key="termsofservice.settings.announce.help"/>
        </p>
        <table>
            <tr>
                <td><label for="announcedProtocol"><fmt:message key="termsofservice.settings.announce.protocol"/></label></td>
                <td><input type="text" id="announcedProtocol" name="announcedProtocol" size="10"
                           value="${admin:escapeHTMLTags(announcedProtocol)}" placeholder="https"/></td>
            </tr>
            <fmt:message key="termsofservice.settings.announce.host.placeholder" var="hostPlaceholder"/>
            <fmt:message key="termsofservice.settings.announce.port.placeholder" var="portPlaceholder"/>
            <tr>
                <td><label for="announcedHost"><fmt:message key="termsofservice.settings.announce.host"/></label></td>
                <td><input type="text" id="announcedHost" name="announcedHost" size="30"
                           value="${admin:escapeHTMLTags(announcedHost)}" placeholder="${admin:escapeHTMLTags(hostPlaceholder)}"/></td>
            </tr>
            <tr>
                <td><label for="announcedPort"><fmt:message key="termsofservice.settings.announce.port"/></label></td>
                <td><input type="text" id="announcedPort" name="announcedPort" size="6"
                           value="${admin:escapeHTMLTags(announcedPort)}" placeholder="${admin:escapeHTMLTags(portPlaceholder)}"/></td>
            </tr>
            <tr>
                <td><label for="announcedContextRoot"><fmt:message key="termsofservice.settings.announce.path"/></label></td>
                <td><input type="text" id="announcedContextRoot" name="announcedContextRoot" size="30"
                           value="${admin:escapeHTMLTags(announcedContextRoot)}" placeholder="${admin:escapeHTMLTags(defaultPath)}"/></td>
            </tr>
        </table>
        <p>
            <fmt:message key="termsofservice.settings.announce.effective"/> <a href="${admin:escapeHTMLTags(effectiveUrl)}"><c:out value="${effectiveUrl}"/></a>
        </p>
    </admin:contentBox>

    <fmt:message key="termsofservice.settings.save" var="saveSettingsLabel"/>
    <input type="submit" name="save" value="${admin:escapeHTMLTags(saveSettingsLabel)}"/>
</form>

</body>
</html>
