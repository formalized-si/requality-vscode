

<#macro showref req text islist tp val refines setname lvl>
	<#if lvl == 0>
		${utils.resetMap(setname)}
	</#if>
	<#if val?? && (islist || ReferenceValue.isInstance(val))>
		<#if (text??&&text?length>0)>
			${text}:
			<br/>
		</#if>
			<#if islist>
				<#if val.getValues??>
					<#list val.getValues() as node>
						<@showref req "" false val.getType() node refines setname lvl+1/>
					</#list>
				</#if>
			<#else>
				<#if val.getNodes?? && val.getNodes(req.attrs())??>
					<#local nodes = val.getNodes(req.attrs())/>
					<#list nodes as node>
						<#local fida = node.attrs().getStringAttributeValue("%SeqID")!""/>
						<#if fida!="">
							<#if !utils.isSameFile(node, req)><#t>
								${utils.addToMap(setname, utils.getLinkTextPrefix(node,req)+fida, utils.getTemplFileName(node)+"#"+fida)}
							<#else>
								${utils.addToMap(setname,fida, "#"+fida)}
							</#if>
						<#else>
							<#if !utils.isSameFile(node, req)><#t>
								${utils.addToMap(setname, utils.getLinkTextPrefix(node,req)+node.getUserVisibleName(), utils.getTemplFileName(node)+"#"+node.getUserVisibleName())}
							<#else>
								${utils.addToMap(setname,node.getUserVisibleName(), "#"+node.getUserVisibleName())}
							</#if>
						</#if>
					</#list>
				<#else>
					<#if val.getRaw??>
						${utils.addToMap(setname,refines.valueToString(req.attrs(),tp,val.getRaw()), "")}
					</#if>
				</#if>
			</#if>
	</#if>
	<#if lvl == 0>
		<div class = "tabind">
		<#local set = utils.getMap(setname)/>
		<#local first = true/>
		<#list set?keys as key>
			<#if first>
				<#local first = false/>
			<#else>
				<br/>
			</#if>
			<#local val = set[key]!"">
			<#if val != "">
				<a href = "${val}">${key}</a>
			<#else>
				<span class = "error">${key}</span>
			</#if>
		</#list>
		</div>
	</#if>
</#macro> 
<#macro showpath req path>
	<#global reqPath = path/>
	<#if (context.reportData.attrs().getBooleanAttributeValue("useProjectNumeration",false))>	<#global reqPath = req.attrs().getStringAttributeValue("_numericId")/></#if>
</#macro>
<#macro showheading req lvl path>
    <#if (coreUtils.isHeader(req))>
        <#local childrenList = req.getSortedShownChildren()/>
        <#if !_showRootData??>
        	<#assign _showRootData=true/>
        </#if>
    	<#local isRoot = req.getUUId() == pageRootReq.getUUId()/>
        <#if (req.getUUId() != pageRootReq.getUUId() || _showRootData == true)>
    		<#local fid = "0"/>
    		<#if req.attrs().getStringAttributeValue("%SeqID")??>
    			<#local fid = req.attrs().getStringAttributeValue("%SeqID")/>
    		</#if>
    		<#local fidLink = fid/>
    		<#if fidLink?string == "0">
    			<#local fidLink = req.getSUUId()/>
    		</#if>
    		<@showpath req path/>
			<li class='toc-level${lvl}'><span class='toc-text'><a href='#${fidLink}'>${reqPath} ${req.getNameOrId()}</a></span><span class='toc-num'>${fid}</span></li>
			<#local i = 1/>
			<#if (path?length>0)>
				<#local path = path+"."/>
			</#if>
			<#list childrenList as child>
				<@showheading child lvl+1 path+i/>
				<#if coreUtils.isHeader(child)>
					<#local i = i + 1/>
				</#if>
			</#list>
		<#else>
			<#if (isRoot)><#local lvl = lvl-1/></#if><#t>
		    <#local i = 1/>
			<#list childrenList as child>
				<@showheading child lvl+1 path+i/>
				<#if coreUtils.isHeader(child)>
					<#local i = i + 1/>
				</#if>
			</#list>
		</#if>
    </#if>
</#macro>
<#macro showrev req nodes setname>
	${utils.resetMap(setname)}
	<#list nodes as node>
		<#local fida = 0/>
		<#if node.attrs().getStringAttributeValue("%SeqID")??>
			<#local fida = node.attrs().getStringAttributeValue("%SeqID")/>
			<#if !utils.isSameFile(node, req)>
				${utils.addToMap(setname, utils.getLinkTextPrefix(node,req)+fida,utils.getTemplFileName(node)+"#"+fida)}
			<#else>
				${utils.addToMap(setname, fida,"#"+fida)}
			</#if>
		</#if>
	</#list>
	<#local set = utils.getMap(setname)/>
	<#list set?keys as key>
		<a href = "${set[key]}">${key}</a><br/>
	</#list>
</#macro>
<#macro showreq req lvl path>
	<#if req.getType() == "Requirement" || req.getType() == "TextNode">
		<#local text = ""/>
		<#local isHeader = coreUtils.isHeader(req)/>
		<#if (lvl>0)>
			<#local fid = req.attrs().getStringAttributeValue("%SeqID")!0/>
			${utils.resetMap("selvals")}
			${utils.resetMap("refNames")}
			<@defineRefRenames/>
			<#list selAttrs as name>
				<#local attr = req.attrs().getAttribute(name)!"">
				<#if (attr == "")>
					<#assign val = ""/>
				<#else>
					<#if attr?isReference()>
						<#assign val = utils.refineRef(req,)/>
					<#else>
					
					</#if>
				</#if>
				<#id req.attrs().getAttribute("")>
				<#assign val = req.attrs().getStringAttributeValue(name)/>
				
				${utils.addToMapList("terms", term, utils.getTemplFileName(node)+"#"+fida)}
			<#list>
			<#local tp = "Требование"/>
			<#if coreUtils.isHeader(req)>
				<#local tp = "Заголовок"/>
			<#else>
				<#if req.attrs().getStringAttributeValue("Вид")??>
					<#local tp = req.attrs().getStringAttributeValue("Вид")/>
				</#if>
			</#if>
			<#local pr = ""/>
			<#if (req.attrs().getStringAttributeValue("Производное"))??>
				<#local pr = req.attrs().getStringAttributeValue("Производное")/>
			</#if>
			<#if isHeader>
				<@showpath req path/>
				<#local text = "<b>"+reqPath+" "+req.getNameOrId()+"</b>"/>
			<#else>
				<#if req.getDescription(reportLocation)?has_content>
					<#local cl = 'requirement'/>
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
							<#local cl = 'textnode-'+kind/>
						</#if>
					</#if>
		            <#local text=req.getDescription("<div class='"+cl+"'>","</div>",reportLocation,
		            										utils.getURIResolver())/>
		        </#if>
		    </#if>
		    <tr>
			<#list selAttrs as name>
				<#if name == "_description">
					<td class="col_${name_index}"><a name="${fid}"></a>${utils.refineValue(text,name,selFormula[name_index])}</td>
				<#else>
					<#if name == "%SeqID">
					<td class="col_${name_index}">${fid}</td>
					<#else>
						<#if name == "Вид">
					<td class="col_${name_index}">${utils.refineValue(tp,name,selFormula[name_index])}</td>
						<#else>
							<#if name == "Производное">
					<td class="col_${name_index}">${utils.refineValue(pr,name,selFormula[name_index])}</td>
							<#else>
					<td class="col_${name_index}">${utils.refineValue(req.attrs().getStringAttributeValue(name, ""),name,selFormula[name_index])}</td>
							</#if>
						</#if>
					</#if>
				</#if>
			</#list>
			<td>
			<#if req.attrs().getAttribute("refines")??>
				<#local refines = req.attrs().getAttribute("refines")/>
				<@showref req "Уточняет" refines.isList() refines.getType() refines.getValue() refines "tablew" 0/>
			</#if>
			<#if utils.getRevertNodes(req, "refines")??>
				Уточняется в:<br>
				<#local nodes = utils.getRevertNodes(req, "refines")/>
				<@showrev req nodes, "ref"/>
			</#if>
			<#if req.attrs().getAttribute("rationale-ref")??>
				<#local refines = req.attrs().getAttribute("rationale-ref")/>
				<@showref req "Обоснование" refines.isList() refines.getType() refines.getValue() refines "tablew" 0/>
			</#if>
			<#if utils.getRevertNodes(req, "rationale-ref")??>
				Обосновывает:<br>
				<#local nodes = utils.getRevertNodes(req, "rationale-ref")/>
				<@showrev req nodes, "rat"/>
			</#if>
			 <#local terms = utils.getDefToUseTerms(req)![]/>
			<#if (terms?size>0)>
			 	${utils.resetMap("terms")}
			 	<#if (terms?is_enumerable && terms?size>0)>
			 		Термины:<br/>
				 	<#list terms as term>
		 				<#local nodes = utils.getDefToUseNodes(req, term)/>
			 			<#list nodes as node>
			 				<#if (node.attrs().getStringAttributeValue("%SeqID")??)>
								<#local fida = node.attrs().getStringAttributeValue("%SeqID")/>
								<#if (fida??&&fida?length>0)>
									<#if !utils.isSameFile(node, req)>
										${utils.addToMapList("terms", term, utils.getTemplFileName(node)+"#"+fida)}
										${utils.addToMapList("terms", term, utils.getLinkTextPrefix(node,req)+fida)}
									<#else>
										${utils.addToMapList("terms", term, "#"+fida)}
										${utils.addToMapList("terms", term, fida)}
									</#if>
								</#if>
							</#if>
			 			</#list>
				 	</#list>
				 	<#local set = utils.getMapList("terms")>
				 	<#list set?keys as key>
				 		<#local seq = set[key]/>
				 		<#if (seq?size>2)>
				 			${key}:<br/> 
				 			<#local b = true/>
				 			<#list seq as node>
				 				<#if b>
				 					<a href = "${node}">
				 				<#else>
				 					${node}</a><#if seq?last != node>, </#if>
				 				</#if>
				 				<#if b><#local b = false/><#else><#local b = true/></#if>
				 			</#list>
				 			<br/>
				 		<#else>
							<a href = "${seq?first}">${key}</a><br/>
						</#if>
				 	</#list>
				 </#if>
			 </#if>
			 <#local termsrevn = utils.getUseToDefTerms(req)![]/>
			 <#if (termsrevn?size>0)>
		 	Использование:
		 		<#local last = ""/>
		 		<#list termsrevn as term>
		 			<#local nodes = utils.getUseToDefTerms(req, term)![]/>
		 			<#if (nodes?size>0)>
			 			<#if (nodes?size>0)><#t>
			 				<#if (nodes?size==1)><#t>
			 					<#local node = nodes?first/>
								<#local fida = 0/><#t>
								<#if node.attrs().getStringAttributeValue("%SeqID")??><#t>
									<#local fida = node.attrs().getStringAttributeValue("%SeqID")/><#t>
									<#if !utils.isSameFile(node, req)><#t>
										<#local link=utils.getTemplFileName(node)+'#'+fida/><#t>
									<#else><#t>
										<#local link='#'+fida/><#t>
									</#if><#t>
								</#if><#t>
			 					<br/><a href = "${link}">${term}</a><#t>
			 				<#else>
				 				<br/>${term}: <#t>
				 				${utils.resetMap("revT")}<#t>
								<#list nodes as node><#t>
									<#local fida = 0/><#t>
									<#if node.attrs().getStringAttributeValue("%SeqID")??><#t>
										<#local fida = node.attrs().getStringAttributeValue("%SeqID")/><#t>
										<#if !utils.isSameFile(node, req)><#t>
											${utils.addToMap("revT", utils.getLinkTextPrefix(node,req)+fida, utils.getTemplFileName(node)+'#'+fida)}<#t>
										<#else><#t>
											${utils.addToMap("revT", fida, "#"+fida)}<#t>
										</#if><#t>
									</#if><#t>
								</#list><#t>
								<#local set = utils.getMap("revT")/><#t>
								<#if (set?size>0)><#t>
									<br/><#t>
									<#list set?keys as key><#t>
										<#local val = set[key]!""/><#t>
											<#if val!=""><#t>
												<a href = "${val}">${key}</a><#t>
											</#if><#t>
										<#if set?keys?last != key>, </#if><#t>
									</#list><#t>
								</#if><#t>
							</#if>
						</#if><#t>
					</#if><#t>
		 		</#list>
		 	</#if>
			<#if utils.getRevertNodes(req, "next-ref")??>
				Ранее:<br>
				<#local nodes = utils.getRevertNodes(req, "next-ref")/>
				<@showrev req nodes "bef"/>
			</#if>
			<#if req.attrs().getAttribute("next-ref")??>
				<#local refines = req.attrs().getAttribute("next-ref")/>
				<@showref req "Далее" refines.isList()  refines.getType() refines.getValue() refines "tablew" 0/>
			</#if>
			</td>
			</tr>
	    </#if>
	    <#if isHeader>
	        <#local childrenList = req.getSortedShownChildren()/>
			<#if (path?length>0)>
				<#local path = path+"."/>
			</#if>
			<#local i = 1/>
			<#list childrenList as child>
				<@showreq child lvl+1 path+i/>
				<#if coreUtils.isHeader(child)>
					<#local i = i + 1/>
				</#if>
			</#list>
		</#if>
	</#if>
</#macro>