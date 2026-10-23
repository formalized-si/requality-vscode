
<#macro showTabs req>
	<div class="pttab">
		<#local projNames = utils.getProjectNames(req)/>
		<#local nameNow = req.getTreeDB().getProjectName()/>
		<#local nameFull = req.getTreeDB().getProjectName()+"["+req.getTreeDB().getLocalName()+"]"/>
		<#local best = ""/>
		<#list projNames as name>
			<#local add = ""/>
			<#local onClick = ""/>
			<#if nameFull==name >
				<#local best = name/>
			<#else>
				<#if nameNow == name && best?length==0>
					<#local best = name/>
				</#if>
			</#if>
		</#list>
		<#list projNames as name>
			<#local add = ""/>
			<#local onClick = ""/>
			<#if best==name>
				<#local add = " ptactive"/>
			<#else>
			    <#local onClick="onclick=\"window.location.href='"+utils.getTemplFileName(utils.getTree( name))+"'\""/>
			</#if>
			<div class="pt pttablinks${add}" ${onClick}>${utils.getHeaderName( name)}</div>
		</#list>
	</div>
	<#if multiPartMode?? && multiPartMode>
		<div class="pttab">
			<#local i = 0>
			<#list multiPartRoots as panelNode>
	   			<#local add = ""/>

				<#local i = i+1>
				<#if (i>9)>
					<#local i = 0/>
					</div>
					<div class="pttab">
				</#if>
				
	   			<#local onClick = ""/>
				<#if panelNode==multiPartRoot>
					<#local add = " ptactive"/>
				<#else>
					<#local onClick="onclick=\"window.location.href='"+utils.getTemplFileName(panelNode)+"'\""/>
				</#if>
		   		<#local name = utils.getMultiPartName(panelNode)/>
				<div class="pt pttablinks${add}" title="${utils.replaceQuotes(utils.replHtmlChars(name),"'")}" ${onClick}>${utils.replaceQuotes(utils.replHtmlChars(name),"'")}</div>
			</#list>
		</div>
	</#if>
</#macro>