<!-- Requires tablecss and <lang>consts to be included to. -->

<#macro showplusminus id>
  <span id = "${id}-plusminus" class="plus">⊕</span>
</#macro>
<#assign revstate = []/>
<#assign max = 0/>
<#macro showtable req rootReq noHeader>
	<#local suffix = req.getSUUId()?replace("-","")/>
	<#local state = utils.getMapVarMeta(rootReq, "repoState",[])/>
	<script>
		function showOnclick(id, set){
			var e = document.getElementById(id+"-plusminus");
			if(e.className == "plus"){
				e.className = "minus"; 
				e.innerHTML = "⊖";
				e = document.getElementById(id+"-list");
				e.className = "";
				set.forEach(one=>{
				  e.appendChild(document.createTextNode("\u00A0\u00A0\u00A0\u00A0"+one));
				  e.appendChild(document.createElement("br"));
				});
			}else{
				e.className = "plus";
				e.innerHTML = "⊕";
				e = document.getElementById(id+"-list");
				e.className = "table-hidden";
				e.innerHTML = '';
			}
		}
		var remSet${suffix} = [];
		var addSet${suffix} = [];
		var modSet${suffix} = [];
		<#if state["HasChanges"]?? && state["HasChanges"]=="true"><#t>
			<#assign mod = state["NodesModified"]![]/><#t>
			<#assign ne = state["NodesAdded"]![]/><#t>
			<#assign rem = state["NodesRemoved"]![]/><#t>
			<#list mod as m>modSet${suffix}.push("${m?j_string}");</#list><#t/>
			<#list ne as m>addSet${suffix}.push("${m?j_string}");</#list><#t/>
			<#list rem as m>remSet${suffix}.push("${m?j_string}");</#list><#t/>
		</#if><#t>
	</script>
	<#if !noHeader>
		<h3>Информация о документе</h3>
	</#if>
	<table width="100%" border = "0">
	<tr>
	<th colspan = "2" align = "center" bgcolor = "#eeeeee"><b>Источник</b></th>
	</tr>
	<tr>
	<td width="10%">Проект:</td><td bgcolor = "#ffffff" width="40%">
	${(state["Origin"]!"Git plugin disconnected or old")?html}
	</td>
	</tr>
	<#if ((state["IsInRepo"]!"false")=="true")>
		<tr>
		<td width="10%">Ревизия:</td>
		<#if state["HasChanges"]?? && state["HasChanges"]=="true"><#t>
			<td bgcolor = "pink" width="40%"><#t>
			${state["Revision"]!""} с локальными изменениями<#t>
			</td><#t>
		<#else><#t>
			<td bgcolor = "#ffffff" width="40%"><#t>
			${state["Revision"]!""}<#t>
			</td><#t>
		</#if><#t>
		</tr>
		<tr>
		<td width="10%">Автор:</td><td bgcolor = "#ffffff" width="40%">
		${(state["Author"]!"")?html}
		</td>
		</tr>
		<tr>
		<td width="10%">Дата ревизии:</td><td bgcolor = "#ffffff" width="40%">
		${state["Date"]!""}
		</td>
		</tr>
		<tr>
		<td width="10%">Комментарий к последнему изменению:</td><td bgcolor = "#ffffff" width="40%"><pre>${(state["Comment"]!"")?html}</pre></td>
        </tr>
		<#if state["HasChanges"]?? && state["HasChanges"]=="true"><#t>
		<tr>
			<td width="10%">Локальные отличия:</td>
			<#local mod = state["NodesModified"]![]/>
			<#local ne = state["NodesAdded"]![]/>
			<#local rem = state["NodesRemoved"]![]/>
			<#local defclass = "table-hidden">
			<td><a href="#" onClick = "showOnclick('mod${suffix}', modSet${suffix})"><@showplusminus "mod${suffix}"/>Изменённые объекты: ${(mod)?size}</a><br/><div id = "mod${suffix}-list" class="${defclass}"></div><#t/>
			<a href="#" onClick = "showOnclick('new${suffix}', addSet${suffix})"><@showplusminus "new${suffix}"/>Новые объекты: ${(ne)?size}</a><br/><div id = "new${suffix}-list" class="${defclass}"></div><#t/>
			<a href="#" onClick = "showOnclick('rem${suffix}', remSet${suffix})"><@showplusminus "rem${suffix}"/>Удалённые объекты: ${(rem)?size}</a><br/><div id = "rem${suffix}-list" class="${defclass}"></div></td>
		</tr> 
		</#if><#t>
	</#if>
	<tr>
	<td colspan = "2" align = "center" bgcolor = "#eeeeee"><b>Параметры генератора</b></td>
	</tr>
	<tr>
	<td>Дата генерации:</td>
	<td>${utils.getVarMeta(pageRootReq, "genData",["DATE"]).getValue()}</td>
	</tr>
	<tr>
	<td>Инициатор:</td>
	<td>${(state["SystemAuthor"]!"")?html}</td>
	</tr>
	<tr>
	<td>Версия инструмента:</td>
	<td>Requality <#local var = utils.getVarMeta(pageRootReq, "requalityVersion",["CONSOLE"])/><#if var??>${var.getValue()}</#if></td>
	</tr>
	</table>
</#macro>
<#macro showheader req noHeader=false>
	<#if !noHeader>
		<#local head1=req.getTreeDB().getNode("/Requirements").attrs().getStringAttributeValue("DocumentTitle",
			((utils.getVarMeta(req, "DocumentTitle",[]))!utils.getEmptyStringTV()).getValue())/>
			
		<#local head2=utils.getHeaderName(req.attrs().getStringAttributeValue("DocumentSubTitle",req.getTreeDB().getProjectName()))/>
		<#if head1!="">
			<h1>${head1}</h1>
		</#if>
		<#if head2!="">
			<h2>${head2}</h2>
		</#if>
	</#if>
<@showtable req rootReq noHeader/>
</#macro>