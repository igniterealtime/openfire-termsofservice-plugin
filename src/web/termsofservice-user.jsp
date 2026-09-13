<%@ page
    import="org.igniterealtime.openfire.plugin.termsofservice.TermsOfServicePlugin,
        org.igniterealtime.openfire.plugin.termsofservice.acceptance.AcceptanceMechanism,
        org.igniterealtime.openfire.plugin.termsofservice.acceptance.TosAcceptanceRecord,
        org.igniterealtime.openfire.plugin.termsofservice.acceptance.TosAcceptanceService,
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
<%@ page import="java.net.URLEncoder" %>

<%@ taglib uri="admin" prefix="admin" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<%
    webManager.init(pageContext);
    final String username = ParamUtils.getParameter(request, "username");
    if (username == null || username.isBlank()) {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No username specified.");
        return;
    }

    final TermsOfServicePlugin plugin = (TermsOfServicePlugin) XMPPServer.getInstance().getPluginManager()
        .getPluginByName("Terms of Service").orElseThrow();
    final TosAcceptanceService acceptanceService = plugin.getAcceptanceService();
    final TosDocumentService documentService = plugin.getDocumentService();

    final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
    final String csrfParam = ParamUtils.getParameter(request, "csrf");
    // Values stored in "errors" are i18n keys (resolved via <fmt:message> below).
    final Map<String, String> errors = new HashMap<>();

    final boolean recordAcceptance = request.getParameter("recordAcceptance") != null;
    if (recordAcceptance) {
        if (csrfCookie == null || csrfParam == null || !csrfCookie.getValue().equals(csrfParam)) {
            errors.put("csrf", "termsofservice.error.csrf");
        }
        final String versionToAccept = ParamUtils.getParameter(request, "versionToAccept");
        if (versionToAccept == null || versionToAccept.isBlank()) {
            errors.put("versionToAccept", "termsofservice.user.error.chooseVersion");
        } else if (documentService.getByVersion(versionToAccept).isEmpty()) {
            errors.put("versionToAccept", "termsofservice.user.error.versionGone");
        }
        if (errors.isEmpty()) {
            acceptanceService.recordAcceptance(username, versionToAccept, AcceptanceMechanism.ADMIN.label());
            webManager.logEvent("recorded terms of service acceptance on behalf of user " + username, "username = " + username + "\nversion = " + versionToAccept);
            response.sendRedirect("termsofservice-user.jsp?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "&recorded=true");
            return;
        }
    }

    final String csrf = StringUtils.randomString(15);
    CookieUtils.setCookie(request, response, "csrf", csrf, -1);
    pageContext.setAttribute("csrf", csrf);
    pageContext.setAttribute("errors", errors);
    pageContext.setAttribute("username", username);

    final List<TosAcceptanceRecord> history = acceptanceService.getAcceptanceHistory(username);
    pageContext.setAttribute("history", history);

    final List<TosDocument> allDocuments = documentService.getAll();
    pageContext.setAttribute("allDocuments", allDocuments);
    pageContext.setAttribute("currentVersion", documentService.getCurrent().map(TosDocument::getVersion).orElse(null));
%>
<html>
<head>
    <title><fmt:message key="termsofservice.user.pageTitle"/></title>
    <meta name="subPageID" content="termsofservice-user"/>
    <meta name="extraParams" content="<%= "username="+URLEncoder.encode(username, StandardCharsets.UTF_8) %>"/>
</head>
<body>

<c:if test="${param.recorded eq 'true'}">
    <admin:infobox type="success"><fmt:message key="termsofservice.user.recorded"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['csrf']}">
    <admin:infobox type="error"><fmt:message key="${errors['csrf']}"/></admin:infobox>
</c:if>
<c:if test="${not empty errors['versionToAccept']}">
    <admin:infobox type="error"><fmt:message key="${errors['versionToAccept']}"/></admin:infobox>
</c:if>

<fmt:message key="termsofservice.user.history.title" var="historyTitle"><fmt:param><c:out value="${username}"/></fmt:param></fmt:message>
<admin:contentBox title="${admin:escapeHTMLTags(historyTitle)}">
    <table class="jive-table" cellpadding="3" cellspacing="0" border="0" width="100%">
        <thead>
        <tr>
            <th><fmt:message key="termsofservice.table.version"/></th>
            <th><fmt:message key="termsofservice.user.table.currentlyApplicable"/></th>
            <th><fmt:message key="termsofservice.user.table.mechanism"/></th>
            <th><fmt:message key="termsofservice.user.table.acceptedAt"/></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach var="record" items="${history}">
            <tr>
                <td><c:out value="${record.version}"/></td>
                <td><c:if test="${record.version eq currentVersion}"><fmt:message key="termsofservice.user.table.yes"/></c:if></td>
                <td><c:out value="${record.mechanism}"/></td>
                <td><fmt:formatDate value="${record.acceptedAt}" pattern="yyyy-MM-dd HH:mm:ss"/></td>
            </tr>
        </c:forEach>
        <c:if test="${empty history}">
            <tr><td colspan="4"><fmt:message key="termsofservice.user.history.none"/></td></tr>
        </c:if>
        </tbody>
    </table>
</admin:contentBox>

<fmt:message key="termsofservice.user.record.title" var="recordTitle"/>
<admin:contentBox title="${admin:escapeHTMLTags(recordTitle)}">
    <p>
        <fmt:message key="termsofservice.user.record.help"/>
    </p>
    <fmt:message key="termsofservice.user.record.confirm" var="confirmMsg"><fmt:param><c:out value="${username}"/></fmt:param></fmt:message>
    <fmt:message key="termsofservice.user.record.submit" var="submitLabel"/>
    <form action="termsofservice-user.jsp" method="post">
        <input type="hidden" name="csrf" value="${admin:escapeHTMLTags(csrf)}"/>
        <input type="hidden" name="username" value="${admin:escapeHTMLTags(username)}"/>
        <table>
            <tr>
                <td><label for="versionToAccept"><fmt:message key="termsofservice.user.record.versionLabel"/></label></td>
                <td>
                    <select id="versionToAccept" name="versionToAccept">
                        <c:forEach var="doc" items="${allDocuments}">
                            <option value="${admin:escapeHTMLTags(doc.version)}" ${doc.version eq currentVersion ? 'selected' : ''}>
                                <c:out value="${doc.version}"/> (<c:out value="${doc.status}"/>)
                            </option>
                        </c:forEach>
                    </select>
                </td>
            </tr>
        </table>
        <input type="submit" name="recordAcceptance" value="${admin:escapeHTMLTags(submitLabel)}"
               onclick="return confirm('${admin:escapeHTMLTags(confirmMsg)}');"/>
    </form>
</admin:contentBox>

</body>
</html>
