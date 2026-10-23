<#include "../lib/projecttabs.ftl" />
<#macro showplusminus id>
  <span id = "${id}-plusminus" class="plus">⊕</span>
</#macro>
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
		<h2 style="margin-bottom:0px;"><div id="reportheader">Информация о документе</div></h2>
	</#if>
	<div id="block">
		<@showTabs req/>
		
		<table class = "comInfo">		
		<tr>
			<th colspan = "2"><b>Источник</b></th>
		</tr>
		<tr>
			<td width="10%"><b>Проект:</b></td>
			<td width="40%">
				${(state["Origin"]!"Git plugin disconnected or old")?html}
			</td>
		</tr>
		<#if ((state["IsInRepo"]!"false")=="true")>
			
			<tr>
			<td width="10%"><b>Ревизия:</b></td>
			<td width="40%">
				<#if state["HasChanges"]?? && state["HasChanges"]=="true">
					${state["Revision"]!""} <font color="red"><b><i>*с локальными изменениями</i></b><font>
				<#else>
					${state["Revision"]!""}<#t>				
				</#if>
			</td>
			</tr>
			
			<tr>
			<td width="10%"><b>Автор:</b></td><td width="40%">
				${(state["Author"]!"")?html}
			</td>
			</tr>
			
			<tr>
			<td width="10%"><b>Дата ревизии:</b></td><td width="40%">
				${state["Date"]!""}
			</td>
			</tr>
			
			<tr>
			<td width="10%"><b>Комментарий к последнему изменению:</b></td><td width="40%"><pre>${(state["Comment"]!"")?html}</pre></td>
            </tr>
				<#if state["HasChanges"]?? && state["HasChanges"]=="true">
				<tr>
				<td width="10%"><b>Локальные отличия:</b></td>
					<#local mod = state["NodesModified"]![]/>
					<#local ne = state["NodesAdded"]![]/>
					<#local rem = state["NodesRemoved"]![]/>
					<#local defclass = "table-hidden">
				<td width="40%"><a href="#" onClick = "showOnclick('mod${suffix}', modSet${suffix})"><@showplusminus "mod${suffix}"/>Изменённые объекты: ${(mod)?size}</a><br/><div id = "mod${suffix}-list" class="${defclass}"></div><#t/>
			<a href="#" onClick = "showOnclick('new${suffix}', addSet${suffix})"><@showplusminus "new${suffix}"/>Новые объекты: ${(ne)?size}</a><br/><div id = "new${suffix}-list" class="${defclass}"></div><#t/>
			<a href="#" onClick = "showOnclick('rem${suffix}', remSet${suffix})"><@showplusminus "rem${suffix}"/>Удалённые объекты: ${(rem)?size}</a><br/><div id = "rem${suffix}-list" class="${defclass}"></div></td>
				</tr> 
				</#if>
		</#if>
		

	<tr>
	<th colspan = "2"><b>Параметры генератора</b></th>
	</tr>
	<tr>
	<td  width="10%"><b>Дата генерации:</b></td>
	<td  width="40%">${utils.getVarMeta(pageRootReq, "genData",["DATE"]).getValue()}</td>
	</tr>
	<tr>
	<td width="10%"><b>Инициатор:</b></td>
	<td width="40%">${(state["SystemAuthor"]!"")?html}</td>
	</tr>
	<tr>
	<td width="10%"><b>Версия инструмента:</b></td>
	<td width="40%">Requality <#local var = utils.getVarMeta(pageRootReq, "requalityVersion",["CONSOLE"])/><#if var??>${var.getValue()}</#if></td>
	</tr>
	</table>
	
	</div>
</#macro>
<#macro showheader req>
<#local head1=utils.getVarMeta(req, "ReportTitle", [""]).getValue()!""/>
<#if head1!="">
	<h1>${head1}</h1>
</#if>
<@showtable req rootReq true/>
</#macro>