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
<#macro showreq req>
    <#local node = req/>
    <#local childrenList = node.getSortedShownChildren()/>
    <#list childrenList as child>
        <#local hasChild = 1/>
    </#list>
    <node qid="${utils.htmlEscape(node.getQualifiedId())}" type="${node.getType()}">
    <#if node.getType() == "Requirement">
      <name>${utils.htmlEscape(node.getUserVisibleName())}</name>
    </#if>
    <#if node.getError() != "">
      <error><![CDATA[${utils.htmlEscape(node.getError())}]]></error>
    </#if>
    
    <#if node.getType() == "TestCase">
      <desc><![CDATA[${utils.htmlSpaces(node.setExpectedResults())}]]></desc>
    </#if>
    <#if node.getADescription(reportLocation)?has_content>
        <desc><![CDATA[${utils.htmlSpaces(node.getADescription(reportLocation))}]]></desc>
    </#if>
    <#if node.getType() == "Requirement"||node.getType() == "TextNode">
    	<#if node.getLocations()?? && node.getLocatonsDescription(reportLocation)??>
      		<ldesc><![CDATA[${utils.htmlSpaces(node.getLocatonsDescription(reportLocation))}]]></ldesc>
      	</#if>
    </#if>
    <#if (hasChild!0) != 0>
        <#list childrenList as child>
            <@showreq child/>
        </#list>
    </#if>
    </node>
</#macro>
<requality>
${utils.initMainGeneration()}<#t/>
<@showreq pageRootReq/>
<#if root.getError() != "">
   <errors><![CDATA[${utils.htmlEscape(root.getError())}]]></errors>
</#if>
</requality>

