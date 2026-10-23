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
<#macro showid req><#if req??&&req.getId()!="Requirements"><#if req.getWorkParent()??><@showid req.getWorkParent()/>/</#if></#if>${req.getNameOrId()}</#macro>
<#macro showreq req lvl add>
    <#local node = req/>
    <#local childrenList = node.getSortedWorkChildren()/>
    <#local hasChild = 0/>
    <#list childrenList as child>
        <#local hasChild = 1/>
    </#list>
    <#if node.getType() == "Requirement">
        <#if hasChild != 0 && (lvl<=2)>
section '<@showid node/>'
        </#if>
        <#if hasChild == 0 || (lvl>2)>
req '${add}${node.getNameOrId()}' is
             <#if node.getDescription(reportLocation)?has_content>
${utils.htmlSpaces(node.getDescription(reportLocation))}
            <#else>
                <#list node.getLocations() as location>
                    <#if (li!0) != 0>
                    </#if>
                    <#if !location.isHidden()>
${utils.htmlSpaces(location.getDescription(reportLocation))}
                    </#if>
                    <#local li = 1/>
                </#list>
        </#if>
endreq
        </#if>
    </#if>
    <#if (hasChild) != 0>
        <#list childrenList as child>
        	<#if (lvl<=2)>
            	<@showreq child lvl+1 add/>
            <#else>
            	<@showreq child lvl+1 add+node.getNameOrId()+"/"/>
            </#if>
            
        </#list>
    </#if>
</#macro>
<@showreq rootReq 0 ""/>


