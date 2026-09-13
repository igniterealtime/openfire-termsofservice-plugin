<%@ page
    import="org.igniterealtime.openfire.plugin.termsofservice.TermsOfServicePlugin,
        org.igniterealtime.openfire.plugin.termsofservice.document.TosDocument,
        org.igniterealtime.openfire.plugin.termsofservice.document.TosDocumentService,
        org.jivesoftware.openfire.XMPPServer,
        org.jivesoftware.util.CookieUtils,
        org.jivesoftware.util.ParamUtils,
        org.jivesoftware.util.StringUtils,
        java.util.HashMap,
        java.util.List,
        java.util.Map"
    errorPage="error.jsp"%>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
    webManager.init(pageContext);

    final TermsOfServicePlugin plugin = (TermsOfServicePlugin) XMPPServer.getInstance().getPluginManager().getPluginByName("Terms of Service").orElseThrow();
    final TosDocumentService documentService = plugin.getDocumentService();

    final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    final String csrfParam = ParamUtils.getParameter(request, "csrf");
    // Values stored in "errors" are i18n keys (resolved via <fmt:message> below), except "activate" which pairs
    // a fixed key with a non-translatable technical detail via a <fmt:param>.
    final Map<String, String> errors = new HashMap<>();

    final boolean createDraft = request.getParameter("createDraft") != null;
    final boolean activate = request.getParameter("activate") != null;

    if (createDraft || activate) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "termsofservice.error.csrf");
        }
    }

    if (createDraft && errors.isEmpty()) {
        final String newVersion = ParamUtils.getParameter(request, "newVersion");
        if (newVersion == null || newVersion.isBlank()) {
            errors.put("newVersion", "termsofservice.documents.error.versionRequired");
        } else if (documentService.getByVersion(newVersion).isPresent()) {
            errors.put("newVersion", "termsofservice.documents.error.versionExists");
        } else {
            final String copyFrom = ParamUtils.getParameter(request, "copyFrom", true);
            if (copyFrom != null && !copyFrom.isBlank()) {
                documentService.copyToNewDraft(copyFrom, newVersion, webManager.getAuthToken().getUsername());
                webManager.logEvent("created new terms of service draft", "version = " + newVersion + "\ncopy from = " + copyFrom);
            } else {
                documentService.createDraft(newVersion, "", webManager.getAuthToken().getUsername());
                webManager.logEvent("created new terms of service draft", "version = " + newVersion);
            }
            response.sendRedirect("termsofservice-document-edit.jsp?version=" + java.net.URLEncoder.encode(newVersion, StandardCharsets.UTF_8) + "&created=true");
            return;
        }
    }

    if (activate && errors.isEmpty()) {
        final String version = ParamUtils.getParameter(request, "version");
        if (version == null || version.isBlank()) {
            errors.put("activate", "termsofservice.documents.error.versionRequired");
        } else {
            try {
                documentService.activate(version, webManager.getAuthToken().getUsername());
                webManager.logEvent("activated terms of service version " + version, null);
                response.sendRedirect("termsofservice-documents.jsp?activated=true");
                return;
            } catch (final Exception e) {
                errors.put("activate", e.getMessage());
            }
        }
    }

    pageContext.setAttribute("enabled", TermsOfServicePlugin.ENABLED.getValue());

    final String csrf = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrf, -1);
    pageContext.setAttribute("csrf", csrf);
    pageContext.setAttribute("errors", errors);

    final List<TosDocument> documents = documentService.getAll();
    pageContext.setAttribute("hasCurrentDocument", documentService.getCurrent().isPresent());
    pageContext.setAttribute("documents", documents);
%>
<html>
<head>
    <title><fmt:message key="termsofservice.documents.title"/></title>
    <meta name="pageID" content="termsofservice-documents"/>
</head>
<body>

<c:if test="${param.activated eq 'true'}">
    <admin:infobox type="success"><fmt:message key="termsofservice.documents.activated"/></admin:infobox>
</c:if>

<c:if test="${not empty errors['csrf']}">
    <admin:infobox type="error"><fmt:message key="${errors['csrf']}"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['activate']}">
    <admin:infobox type="error">
        <fmt:message key="termsofservice.documents.error.activateFailed"><fmt:param value="${errors['activate']}"/></fmt:message>
    </admin:infobox>
</c:if>
<c:if test="${not empty errors['newVersion']}">
    <admin:infobox type="error"><fmt:message key="${errors['newVersion']}"/></admin:infobox>
</c:if>
<c:if test="${enabled and not hasCurrentDocument}">
    <admin:infobox type="warning">
        <fmt:message key="termsofservice.warning.noCurrentDocument"/>
    </admin:infobox>
</c:if>
<admin:infobox type="info">
    <fmt:message key="termsofservice.info.sasl2Only"/>
</admin:infobox>

<fmt:message key="termsofservice.documents.title" var="documentsTitle"/>
<admin:contentBox title="${admin:escapeHTMLTags(documentsTitle)}">
    <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th><fmt:message key="termsofservice.table.version"/></th>
            <th><fmt:message key="termsofservice.table.status"/></th>
            <th><fmt:message key="termsofservice.table.created"/></th>
            <th><fmt:message key="termsofservice.table.updated"/></th>
            <th><fmt:message key="termsofservice.table.activated"/></th>
            <th><fmt:message key="termsofservice.table.replaced"/></th>
            <th><fmt:message key="termsofservice.table.actions"/></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="doc" items="${documents}">
            <tr>
                <td><c:out value="${doc.version}"/></td>
                <td><c:out value="${doc.status}"/></td>
                <td>
                    <fmt:formatDate value="${doc.createdAt}" pattern="yyyy-MM-dd HH:mm" var="createdAtFmt"/>
                    <fmt:message key="termsofservice.table.dateBy"><fmt:param value="${createdAtFmt}"/><fmt:param><c:out value="${doc.createdBy}"/></fmt:param></fmt:message>
                </td>
                <td>
                    <fmt:formatDate value="${doc.updatedAt}" pattern="yyyy-MM-dd HH:mm" var="updatedAtFmt"/>
                    <fmt:message key="termsofservice.table.dateBy"><fmt:param value="${updatedAtFmt}"/><fmt:param><c:out value="${doc.updatedBy}"/></fmt:param></fmt:message>
                </td>
                <td><c:if test="${not empty doc.activatedAt}"><fmt:formatDate value="${doc.activatedAt}" pattern="yyyy-MM-dd HH:mm"/></c:if></td>
                <td><c:if test="${not empty doc.replacedAt}"><fmt:formatDate value="${doc.replacedAt}" pattern="yyyy-MM-dd HH:mm"/></c:if></td>
                <td>
                    <a href="termsofservice-document-edit.jsp?version=${admin:urlEncode(doc.version)}">
                        <c:choose>
                            <c:when test="${doc.status eq 'DRAFT'}"><fmt:message key="termsofservice.action.edit"/></c:when>
                            <c:otherwise><fmt:message key="termsofservice.action.view"/></c:otherwise>
                        </c:choose>
                    </a>
                    <c:if test="${doc.status eq 'DRAFT'}">
                        |
                        <fmt:message key="termsofservice.documents.confirmActivate" var="confirmActivateMsg"><fmt:param><c:out value="${doc.version}"/></fmt:param></fmt:message>
                        <fmt:message key="termsofservice.action.activate" var="activateLabel"/>
                        <form style="display:inline" action="termsofservice-documents.jsp" method="post">
                            <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>
                            <input type="hidden" name="version" value="${admin:escapeHTMLTags(doc.version)}"/>
                            <input type="submit" name="activate" value="${admin:escapeHTMLTags(activateLabel)}"
                                   onclick="return confirm('${admin:escapeHTMLTags(confirmActivateMsg)}');"/>
                        </form>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty documents}">
            <tr><td colspan="7"><fmt:message key="termsofservice.documents.none"/></td></tr>
        </c:if>
        </tbody>
    </table>
</admin:contentBox>

<fmt:message key="termsofservice.documents.createNew.title" var="createNewTitle"/>
<admin:contentBox title="${admin:escapeHTMLTags(createNewTitle)}">
    <form action="termsofservice-documents.jsp" method="post">
        <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>
        <table>
            <tr>
                <td><label for="newVersion"><fmt:message key="termsofservice.documents.newVersion.label"/></label></td>
                <fmt:message key="termsofservice.documents.newVersion.placeholder" var="newVersionPlaceholder"/>
                <td><input type="text" id="newVersion" name="newVersion" size="30"
                           placeholder="${admin:escapeHTMLTags(newVersionPlaceholder)}"/></td>
            </tr>
            <tr>
                <td><label for="copyFrom"><fmt:message key="termsofservice.documents.copyFrom.label"/></label></td>
                <td>
                    <select id="copyFrom" name="copyFrom">
                        <option value=""><fmt:message key="termsofservice.documents.copyFrom.blank"/></option>
                        <c:forEach var="doc" items="${documents}">
                            <option value="${admin:escapeHTMLTags(doc.version)}"><c:out value="${doc.version}"/> (<c:out value="${doc.status}"/>)</option>
                        </c:forEach>
                    </select>
                </td>
            </tr>
        </table>
        <fmt:message key="termsofservice.documents.create" var="createLabel"/>
        <input type="submit" name="createDraft" value="${admin:escapeHTMLTags(createLabel)}"/>
    </form>
</admin:contentBox>

</body>
</html>
