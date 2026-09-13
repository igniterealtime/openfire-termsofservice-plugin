<%@ page
    import="org.igniterealtime.openfire.plugin.termsofservice.TermsOfServicePlugin,
        org.igniterealtime.openfire.plugin.termsofservice.document.TosDocument,
        org.igniterealtime.openfire.plugin.termsofservice.document.TosDocumentService,
        org.igniterealtime.openfire.plugin.termsofservice.document.TosDocumentStatus,
        org.jivesoftware.openfire.XMPPServer,
        org.jivesoftware.util.CookieUtils,
        org.jivesoftware.util.ParamUtils,
        org.jivesoftware.util.StringUtils,
        java.util.HashMap,
        java.util.Map,
        java.util.Optional"
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

    final String version = ParamUtils.getParameter(request, "version");
    if (version == null || version.isBlank()) {
        response.sendRedirect("termsofservice-documents.jsp");
        return;
    }

    final Optional<TosDocument> existing = documentService.getByVersion(version);
    if (existing.isEmpty()) {
        response.sendRedirect("termsofservice-documents.jsp");
        return;
    }
    TosDocument document = existing.get();

    final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    final String csrfParam = ParamUtils.getParameter(request, "csrf");
    // Values stored in "errors" are i18n keys (resolved via <fmt:message> below).
    final Map<String, String> errors = new HashMap<>();

    final boolean save = request.getParameter("save") != null;
    if (save) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "termsofservice.error.csrf");
        }
        if (document.getStatus() != TosDocumentStatus.DRAFT) {
            errors.put("save", "termsofservice.documentEdit.notEditable"); // rendered with the status as its {0} param below
        }
        if (errors.isEmpty()) {
            final String body = ParamUtils.getParameter(request, "body", true);
            document = documentService.updateDraft(version, body == null ? "" : body, webManager.getAuthToken().getUsername());
            webManager.logEvent("updated terms of service draft", "version = " + version);
            response.sendRedirect("termsofservice-document-edit.jsp?version=" + java.net.URLEncoder.encode(version, StandardCharsets.UTF_8) + "&saved=true");
            return;
        }
    }

    final String csrf = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrf, -1);
    pageContext.setAttribute("csrf", csrf);
    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("document", document);
    pageContext.setAttribute("editable", document.getStatus() == TosDocumentStatus.DRAFT);
%>
<html>
<head>
    <title><fmt:message key="termsofservice.documentEdit.pageTitle"><fmt:param><c:out value="${document.version}"/></value></fmt:param></fmt:message></title>
    <meta name="pageID" content="termsofservice-documents"/>
</head>
<body>

<c:if test="${param.created eq 'true'}">
    <admin:infobox type="success"><fmt:message key="termsofservice.documentEdit.created"/></admin:infobox>
</c:if>
<c:if test="${param.saved eq 'true'}">
    <admin:infobox type="success"><fmt:message key="termsofservice.documentEdit.saved"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['csrf']}">
    <admin:infobox type="error"><fmt:message key="${errors['csrf']}"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['save']}">
    <admin:infobox type="error">
        <fmt:message key="${errors['save']}"><fmt:param><c:out value="${document.status}"/></fmt:param></fmt:message>
    </admin:infobox>
</c:if>

<p>
    <a href="termsofservice-documents.jsp"><fmt:message key="termsofservice.nav.backToVersions"/></a>
</p>

<fmt:message key="termsofservice.documentEdit.boxTitle" var="boxTitle"><fmt:param><c:out value="${document.version}"/></fmt:param><fmt:param><c:out value="${document.status}"/></fmt:param></fmt:message>
<admin:contentBox title="${admin:escapeHTMLTags(boxTitle)}">
    <table>
        <tr>
            <td><fmt:message key="termsofservice.documentEdit.createdLabel"/></td>
            <td>
                <fmt:formatDate value="${document.createdAt}" pattern="yyyy-MM-dd HH:mm" var="createdAtFmt"/>
                <fmt:message key="termsofservice.table.dateBy"><fmt:param value="${createdAtFmt}"/><fmt:param><c:out value="${document.createdBy}"/></fmt:param></fmt:message>
            </td>
        </tr>
        <tr>
            <td><fmt:message key="termsofservice.documentEdit.updatedLabel"/></td>
            <td>
                <fmt:formatDate value="${document.updatedAt}" pattern="yyyy-MM-dd HH:mm" var="updatedAtFmt"/>
                <fmt:message key="termsofservice.table.dateBy"><fmt:param value="${updatedAtFmt}"/><fmt:param><c:out value="${document.updatedBy}"/></fmt:param></fmt:message>
            </td>
        </tr>
        <c:if test="${not empty document.activatedAt}">
            <tr><td><fmt:message key="termsofservice.documentEdit.activatedLabel"/></td><td><fmt:formatDate value="${document.activatedAt}" pattern="yyyy-MM-dd HH:mm"/></td></tr>
        </c:if>
        <c:if test="${not empty document.replacedAt}">
            <tr><td><fmt:message key="termsofservice.documentEdit.replacedLabel"/></td><td><fmt:formatDate value="${document.replacedAt}" pattern="yyyy-MM-dd HH:mm"/></td></tr>
        </c:if>
    </table>

    <c:choose>
    <c:when test="${editable}">
        <form action="termsofservice-document-edit.jsp" method="post">
            <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>
            <input type="hidden" name="version" value="${admin:escapeHTMLTags(document.version)}"/>
            <p>
                <label for="body"><fmt:message key="termsofservice.documentEdit.bodyLabel"/></label><br/>
                <textarea id="body" name="body" rows="24" cols="100"><c:out value="${document.markdownBody}"/></textarea>
            </p>
            <fmt:message key="termsofservice.documentEdit.save" var="saveLabel"/>
            <input type="submit" name="save" value="${admin:escapeHTMLTags(saveLabel)}"/>
        </form>
        <p>
            <fmt:message key="termsofservice.documentEdit.notVisibleYet1"/>
            <a href="termsofservice-documents.jsp"><fmt:message key="termsofservice.documentEdit.notVisibleYetLink"/></a>.
        </p>
    </c:when>
    <c:otherwise>
        <p><fmt:message key="termsofservice.documentEdit.notEditable"><fmt:param><c:out value="${document.status}"/></fmt:param></fmt:message></p>
        <pre style="white-space: pre-wrap; border: 1px solid #ccc; padding: 8px;"><c:out value="${document.markdownBody}"/></pre>
        <p>
            <fmt:message key="termsofservice.documentEdit.copyForward1"/>
            <a href="termsofservice-documents.jsp"><fmt:message key="termsofservice.documents.title"/></a>
            <fmt:message key="termsofservice.documentEdit.copyForward2"><fmt:param><c:out value="${document.version}"/></fmt:param></fmt:message>
        </p>
    </c:otherwise>
    </c:choose>
</admin:contentBox>

</body>
</html>
