<#--
/* Copyright 2009-2026 ISP RAS (http://www.ispras.ru), UniTESK Lab (http://www.unitesk.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 -->
<#macro showreq req lvl>
    <#local node = req/>
    <#local childrenList = node.getSortedShownChildren()/>
    <#list childrenList as child>
        <#if child.getType() = "Requirement">
            <#local hasChild = 1/>
        </#if>    
    </#list>
    <#if (hasChild!0) != 0>
        <h${lvl} class="header-${lvl}"><strong>${utils.htmlEscape(node.getNameOrId())}</strong></h${lvl}>
        <div>
        <#if node.getDescription(reportLocation)?has_content>
            ${utils.htmlSpaces(node.getDescription(reportLocation))}
        <#else>
            <#list node.getLocations() as location>
                <#if (li!0) != 0>
                    <br/>...
                </#if>
                <#if !location.isHidden()>
                    ${utils.htmlSpaces(location.getDescription(reportLocation))}
                </#if>
                <#local li = 1/>
            </#list>
        </#if>
        </div>
        <#list childrenList as child>
            <#if child.getType() = "Requirement">
                <@showreq child lvl+1/>
            </#if>    
        </#list>
    <#else>
        <div>
        <#if node.getDescription(reportLocation)?has_content>
            ${utils.htmlSpaces(node.getDescription(reportLocation))}
        <#else>
            <#list node.getLocations() as location>
                <#if (li!0) != 0 >
                    <br/>...
                </#if>
                <#if !location.isHidden()>
                    ${utils.htmlSpaces(location.getDescription(reportLocation))}
                </#if>
                <#local li = 1/>
            </#list>
        </#if>
        </div>
    </#if>    
</#macro>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<style>
	.notResolved   { background: pink }
</style>
</head>
<body>
${utils.initMainGeneration()}<#t/>
<@showreq rootReq 1/>
<!-- ${.now?iso_local} -->
</body>
</html>
