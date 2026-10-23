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
<#assign globalheaders = true/>
<#assign typeMap = { "примечание": "note", "обоснование": "just","описание": "def","":""}/>
<#macro showreq req lvl>
	<#local node = req/>
	<#local childrenList = node.getSortedShownChildren()/>
    <#list childrenList as child>
        <#if (child.getType()=="Requirement" || child.getType()=="TextNode")>
            <#local hasChild = 1/>
        </#if>    
    </#list>
    <#local isuseless=true/>
    <#local descr=""/>
	<#if (lvl!=1)>
	    <#local isheader=((hasChild!0) != 0 || req.getType()=="TextNode" && req.attrs().getStringAttributeValue?? 
	    && req.attrs().getStringAttributeValue("_textNodeType")?? && (req.attrs().getStringAttributeValue("_textNodeType")=="header"))/>
	    <#local kind=""/>
	    <#local kattr=req.attrs().getStringAttributeValue("Вид","")?lower_case/>
	    <#if !kattr?? || kattr?length==0>
	    	<#if req.getType()=="TextNode">
	    		<#local kattr="описание"/>
	    	</#if>
	    </#if>
		<#if kattr??&&typeMap[kattr]??>
			<#local kind = typeMap[kattr]/>
			<#if kind?? && (kind?length>0)>
				<#local kind = "-"+kind/>
			</#if>
		</#if>
	    <#local cname="requirement"/>
	    <#local tcname="requirement${kind}"/>
	    <#if node.getType()="TextNode">
			<#local cname="textnode"/>
	    	<#local tcname="textnode${kind}"/>
	    	<#if node.getTextNodeType??&&node.getTextNodeType()=="header">
	    		<#local descr=""/>
	    	</#if>
	    	<#if node.attrs().getAttributeValue("coverageStatus")??&& 
	    	node.attrs().getAttributeValue("coverageStatus")=='in_process'>
	    		<#local cname="review"/>
	    		<#local tcname="review"/>
	    	</#if>
	    </#if>
	    <#if node.getDescription(reportLocation)?has_content>
	    	<#local descr=utils.htmlSpaces(utils.brTransform(node.getDescription(reportLocation)))/>
	    	<#local descr = utils.reworkDescription(descr,tcname,node.attrs().getAttributeValue("coverageStatus"))/>
	    <#else>
	    	<#if node.getLocations??>
		        <#list node.getLocations() as location>
		            <#if (li!0) != 0>
		                <#local descr=descr+"<br/>"/>
		            </#if>
		            <#if !location.isHidden()>
		           		<#local descr=(descr+utils.htmlEncapsulate(utils.htmlSpaces(utils.brTransform(location.getDescription(reportLocation)))))/>
		    		</#if>
		            <#local li = 1/>
		        </#list>
		        <#local descr = utils.reworkDescription(utils.htmlSpaces(utils.htmlEncapsulate(descr)),node.getType(),node.attrs().getAttributeValue("coverageStatus"))/>
		    </#if>
	    </#if>
	    <#local addS=""/>
	  
	    <#if node.getType()="TextNode">
	    	<#if node.getTextNodeType??&&node.getTextNodeType()=="header">
	    		<#local descr=""/>
	    	</#if>
	    </#if>
	   	<#if node.getType()=="Comment">
	    	<#local cname="todo"/>
	    	<#local tcname="todo"/>
	   	</#if>
	    <#if (isheader!) != false || node.getType() = "Requirement" || (node.attrs().getBooleanAttributeValue("ignoreHeader",true)?? 
	    && node.attrs().getBooleanAttributeValue("ignoreHeader",true)==false || lastUselseness || globalheaders?? && globalheaders)>
	    	<#local halflvl=lvl/>
	    	<#if (lvl>3)>
	    		<#local halflvl=3/>
	    	</#if>
	    	
			<h${halflvl}><a id="${node.getQualifiedId()}-header"></a><#local shortid=utils.htmlSpaces(node.getNameOrId())/>${node.getFullName(true)}</h${halflvl}>
	    </#if>
	    <#if (descr?trim?length>0)>
	    	<#local isuseless=false/>
	    	<p class="${tcname}"><a id="${node.getQualifiedId()}-text"></a>${descr}</p>
	    </#if>
	   	<#list node.attrs().getAllAttributeKeys() as key>
	   		<#if utils.filter(node, attrFilter, key)>
	   			<#local isuseless=false/>
	   			<#local vals = node.attrs().getStringArrayAttributeValue(key)/>
	   			<#list vals as val>
		   			<#local sval = val/>
		   			<#local addval = ""/>
		   			<#if (node.attrs().isReference(key) && relMan.getLinksOrErrorsValues(node,key)?size>0)>
		   				<#local links = relMan.getLinksOrErrorsValues(node,key)/>
		   				<#list links as link>
			   				<#if (link??)>
			   					<#if link.hasErrorsOnResolve?? &&(link.hasErrorsOnResolve(node.attrs()))>
			   						<#local sval = "<font color='red'>"+link.getRaw()+"</font>"/>
			   					<#else>
			   						<#local sval = ""/>
			   						<#if link.getNodes??>
					   					<#local nodes = link.getNodes(node.attrs())/>
					   					<#list nodes as node1>
					   						<#if (sval?length>0)>
					   							<#local sval = sval + ", "/>
					   						</#if>
						   					<#local sval=sval+node1.getUserVisibleName(false)/>
						   				</#list>
					   				</#if>
			   					</#if>
			   				</#if>
			   			</#list>
		   			</#if>
		   			<#if addval!="">
		   				<#local addval = " "+addval/>
		   			</#if>
		   			<p class="attribute"><a id="${node.getQualifiedId()}-attribute-${key}"></a>${key}: ${sval}${addval}</p>
	   			</#list>
	   		</#if>
	   	</#list>
	</#if>
	<#assign lastUselseness=isuseless/>
	<#list childrenList as child>
         <#if child.getType() = "Requirement" || child.getType() = "TextNode"|| child.getType() = "Comment">
         	 <@showreq child lvl+1/>
        </#if>   
    </#list>
</#macro>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<style type="text/css">

	.notResolved   { background: pink }
	p { margin: 1mm }
	table, th, td { border: 1px solid black; }
	table { border-collapse: collapse; margin-top: 5mm }
	body {
		font-family: sans-serif;
	}
	img.plusminus {
		cursor: pointer;
		margin-left: -18px;
		float: left;
		width: 9px;
		height: 9px;
	}
	img.imgstatus {
		cursor: pointer;
		border: solid 1px transparent;
		border-radius: 3px;
		-moz-border-radius: 3px;
		-webkit-border-radius: 3px;
		padding: 4px;
	}
	.toolsPanel{
		position: fixed;
		top: 0;
		left:0;
    	width: 100%;
    	background-color: #dbe3f6;
		padding: 10px;
	}
	.toolsButton{
		border: solid 1px gray;
		cursor: pointer;
		border-radius: 3px;
		-moz-border-radius: 3px;
		-webkit-border-radius: 3px;
		margin: 6px;
		padding: 4px;
		background-color: #e9f1fa;
		background-image: linear-gradient(bottom, #dbe3f6 0%, #f7ffff 100%);
		background-image: -moz-linear-gradient(bottom, #dbe3f6 0%, #f7ffff 100%);
		background-image: -webkit-linear-gradient(bottom, #dbe3f6 0%, #f7ffff 100%);
	}
	.indent{
		margin-left: -1px;
	}
	.reqctrls {
		margin-right: 3px;
	}
	.cmtctrls {
		 margin: 3px 3px 0 0;
	}
	.requirement {
		background-color: #ffefff;
	}
	.todo{
		background-color: #ffffef;
	}
	.attribute {
		background-color:#eff6ff;
	}
	.requirement {
		background-color: #ffefff;
	}
	
	.droppable {
		background-color: #efffff;
		border-color: #4f6f8f;
	}
	.disabled {
		opacity: 0.4;
	}
	.textnode{
		background-color: #ccffcc;
	}
	.meta{
		background-color:#fafafa;
	}
	
	.textnode-def  {
		background: #ccffcc
	}
	.textnode-just  {
		background: #daebdc
	}
	.textnode-note {
		background: #99ff66
	}
	.review{
		background-color: #FFC04D;
	}
</style>

</head>
<body>
<p class="meta">root: ${pageRootReq.getFullName(true)}</p>
<#if !attrFilter??>
	<#assign attrFilter = ['*ref!term*','use-term','def-term','ForeignID']/>
</#if>

<#assign lastUselseness=false/>
${utils.initMainGeneration()}<#t/>
<@showreq pageRootReq 1/>
<!-- ${.now?iso_local} -->
</body>
</html>
