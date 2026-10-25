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
 
<#--
/*	Optional variables:
*		1. noAddTestButtons - format String. Default value "false".
*		2. coverageFilePath - using Eclipse plugin need to press 'Update Coverage Source' button on report settings properties.
*		3. coverageStorageType - using console mode please set 'coverageFilePath' parameter as -coverageFilePath="path to coverage info file".
*/
-->	

<#include "../lib/ruconsts.ftl" />
<#include "../lib/comInfoTable.ftl" />
<#macro getCoverageClass node>
    <#local covnum = coverage.getCoverageClass(node)/>
    <#local covclass = "red"/>
    <#if covnum = 1>
    	<#local covclass = "yellow"/>
    <#else>
    	<#if covnum = 2>
    		<#local covclass = "green"/>
        <#else>	
			<#if covnum = 4>
				<#local covclass = "gray"/>
			</#if>
    	</#if>
    </#if>
    <#assign cclass = covclass/>
</#macro>

<#macro errordetect bool><#assign err=bool/></#macro>

<#macro finderror ferr lvl>
    <#local node = ferr/>
    <#local childrenList = node.getSortedShownChildren()/>
    <#list childrenList as child>
        <#if child.getType() = "Requirement">
            <#local hasChild = 1/>
        <#else>
        	<#if child.getType() = "TestPurpose">
            	<#local hasChild = 1/>
        	</#if>    
        </#if>    
    </#list>
    <#local testsCount = coverage.getCoverageTestCount(node.getQualifiedId())/>
	<#if (testsCount>0)>
    	<#local testPathList = coverage.getCoveragePaths(node.getQualifiedId())/>
		<#list testPathList as path>			
			<#if coverage.hasErrors(node.getQualifiedId(),path)><@errordetect 1/></#if>
		</#list>					    
    </#if> 
	
	<#if (hasChild!0) != 0>
        <#local childrenTestPurposeList = node.getSortedShownChildren("TestPurpose")/>		
        <#list childrenTestPurposeList as childTestPurpose>
			<#local testsCount = coverage.getCoverageTestCount(childTestPurpose.getQualifiedId())/>
			<#local testPathList = coverage.getCoveragePaths(childTestPurpose.getQualifiedId())/>
				<#if (testsCount>0)>
					<#list testPathList as path>					
						<#if coverage.hasErrors(childTestPurpose.getQualifiedId(),path)><@errordetect 1/></#if>															
					</#list>                
				</#if>                		
        </#list>
	</#if>
		
	<#list childrenList as child>
          <#if child.getType() = "Requirement"><@finderror child lvl+1/></#if> 
    </#list>
</#macro>

<#macro num bl><#assign block= bl/></#macro>

<#macro showreq req lvl>
    <#local node = req/>
    <#local childrenList = node.getSortedShownChildren()/>
    <@getCoverageClass node/>
    <#local covclass = cclass/>
    <#list childrenList as child>
        <#if coverage.isCoverable(child, context)>
            <#local hasChild = 1/>
        </#if>    
    </#list>
    <#local testsCount = coverage.getCoverageTestCount(node.getQualifiedId())/>
    <#if (testsCount>0)>
    	<#local testPathList = coverage.getCoveragePaths(node.getQualifiedId())/>
    	<#local testPathLabels = coverage.getPathsLabels(node.getQualifiedId())/>
    	<#local testCntList = coverage.getCoverageCounts(node.getQualifiedId())/>
    </#if>
	<#local covYellowInfo=coverage.getYellowAddInfo(node)/>
	<@errordetect 0/>
	<#if (testsCount>0)>   	
		<#list testPathList as path>			
			<#if coverage.hasErrors(node.getQualifiedId(),path)><@errordetect 1/></#if>
		</#list>
	</#if>
	<#if err==0>
		<#if (hasChild!0) != 0><#list childrenList as child>
            <@finderror child lvl+1/>
        </#list></#if>
	</#if> 	
	
	<#if err==1>
		<h${lvl} style="margin-bottom:0px;" class="markerror"><table style="border-radius:5px 5px 0px 0px; border: outset 1px ${covclass}; width:100%;" class="${covclass}">
			<tr>
				<td onClick="toggle('block${block}', '${block}');">
					<img id="${block}" class="base64" width="20px" height="20px" src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs%3D" onLoad="this.onload=null; this.src=get_png_image_src(0);"/>
					<text style="text-align:center;"><strong>${utils.htmlEscape(node.getUserVisibleName(true))} ${covYellowInfo}</strong></text><text style="color:red"><strong> *</strong></text>
				</td>
				<#if showbuttons><td><button class="addtest" style="float:right;" onclick='callAddTest("${node.getQualifiedId()}");return false'>Add test</button></td></#if>
			</tr>
		</table></h${lvl}>
    	<div id = "block${block}" class="block" style="border-color:${covclass}">
	<#else>
		<h${lvl} style="margin-bottom:0px;"><table style="border-radius:5px 5px 0px 0px; border: outset 1px ${covclass}; width:100%;" class="${covclass}">
			<tr>
			<td onClick="toggle('block${block}', '${block}');">
				<img id="${block}" class="base64" width="20px" height="20px" src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs%3D" onLoad="this.onload=null; this.src=get_png_image_src(0);"/>
				<text style="text-align:center"><strong>${utils.htmlEscape(node.getUserVisibleName(true))} ${covYellowInfo}</strong></text></td>
			<#if showbuttons><td><button class="addtest" style="float:right;" onclick='callAddTest("${node.getQualifiedId()}");return false'>Add test</button></td></#if>
			</tr>
		</table></h${lvl}>
    	<div id = "block${block}" class="block" style="border-color:${covclass}">
	</#if>	
   <@num block+1/>
   <div style="margin:10px;">
    <#if node.getDescription(reportLocation)?has_content>
        ${utils.htmlSpaces(node.getDescription(reportLocation))}
    <#else>
    	<#if node.getType() == "Requirement">
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
    </#if>
    <#if (testsCount>0)>   	
    	<div onclick = "toggletest('testblock${block}', 'title${block}');" style="margin:10px;">
    		<img class="title${block} base64" style="display:inline; float:left;" width="20px" height="20px" src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs%3D" onLoad="this.onload=null; this.src=get_png_image_src(1);"/>
			<text class="title${block}" style="text-align:left; font-weight: bold; border-bottom: 1px dashed;">Развернуть список тестов</text>
		</div>
		<div class="hidden" id = "testblock${block}">
    		<#local iterate = 0/>
			<#list testPathList as path>			
					  <table style="text-align:left; word-break: break-all; width:100%">
						<tr>
							<#assign pathText=utils.getValueByPathFormula(req, testPathLabels[path]!path, testFormula)/>
							<td style="width:45px;"><strong>Test: </strong></td>
							<td><a onclick = 'if(typeof openEditor != "undefined"){if(openEditor("${path}")!=null)return false;}window.location.href="${path}";' href = "#">${pathText}</a></td>
							<#if (testCntList[iterate]>-1)>
								<td rowspan = "2" style="padding-left:30px;"> 
							<#else>
								<td rowspan = "1" style="padding-left:30px;">
							</#if>
								<#if coverage.hasErrors(node.getQualifiedId(),path)> 
    	    						<table class="errortable" max-width = "50%" align = "right">
    	    							<tr>
    	    								<th style="padding:5px;">Наименование ошибки</th>
    	    								<th style="padding:5px;">Описание ошибки</th>
    	    							</tr>
    	    							<#local errors_names=coverage.getErrorNamesForNodeAndTest(node.getQualifiedId(),path)/>
    	    							<#local errors_desc=coverage.getErrorDescriptionsForNodeAndTest(node.getQualifiedId(),path)/>
					        			<#local errors_links=coverage.getErrorLinksForNodeAndTest(node.getQualifiedId(),path)/>       			       			        			
					        			<#list 0..errors_names?size-1 as i>
	        								<#local error_name=(errors_names[i])>
        									<#local error_desc=errors_desc[i]>
				        					<#local error_link=errors_links[i]>
        									<tr>
												<td style="text-align:center"><#if error_link!=""><a title='${error_link}' onclick = 'if(typeof openEditor != "undefined"){if(openEditor("${error_link}")!=null)return false;}window.location.href="${error_link}";' href = "#">${error_name}</a><#else>${error_name}</#if></td>									
												<td style="text-align:center">${error_desc}</td>
    		    							</tr>
										</#list>  
    	    						</table>
								</#if>
							</td>
						</tr>
						<#if (testCntList[iterate]>-1)>
							<tr>
						   		<td style="width:45px;"><strong>Hits: </strong></td>
						   		<td>${testCntList[iterate]}</td>
							</tr>
						</#if>
					  </table>
				<br/>				
    		<#local iterate = iterate + 1/>
            </#list>		
        </div>			    
    </#if> 
	</div>
    <#if (hasChild!0) != 0>
        <#local childrenTestPurposeList = node.getSortedShownChildren("TestPurpose")/>
        	<#if childrenTestPurposeList?size != 0>
                   <table class="report">
                      <tr>
                       	<th>Номер</th>
                       	<th>Описание</th>
                       	<th>Ожидаемые результаты</th>
                       	<th>Teсты</th>
                       	<th>Статус</th>
                  	  </tr>
		            <#list childrenTestPurposeList as childTestPurpose>
        			<@getCoverageClass childTestPurpose/>
					  <tr class="${cclass}">
						<td>${utils.htmlEscape(childTestPurpose.getId())}</td>
						<td>${utils.htmlSpaces(childTestPurpose.getDescription(reportLocation))!""}</td>
						<td>${utils.htmlSpaces(childTestPurpose.getExpectedResults(reportLocation))}</td>
						<td style="text-align:right;">
							<#local testsCount = coverage.getCoverageTestCount(childTestPurpose.getQualifiedId())/>
							<#local testPathList = coverage.getCoveragePaths(childTestPurpose.getQualifiedId())/>
							<#local testCntList = coverage.getCoverageCounts(childTestPurpose.getQualifiedId())/>
							
							<table style="width:100%"><tr>
								<#if (testsCount>1)>
									<td style="text-align:left; border: none;" >
										<div style="width:18em; white-space: nowrap; cursor: pointer; cursor: hand;" onClick="toggletest('block${block}', '${block}');">
										<img class="${block} base64" style="display:inline; float:left;" width="20px" height="20px" src="data:image/gif;base64,R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs%3D" onLoad="this.onload=null; this.src=get_png_image_src(1);"/>
										<text class="${block}" style="text-align:left; font-weight: bold; border-bottom: 1px dashed;">Развернуть список тестов</text></div>
									</td>
								</#if>
								<#if showbuttons>
									<td style="text-align:right; vertical-align:middle; border: none;"><button class="addtest" onclick='callAddTest("${childTestPurpose.getQualifiedId()}");return false'>Add test</button></td></#if> 
							</tr></table>					
							
							<#if (testsCount>0)>
								<#local iterate = 0/>
							
								<#if (testsCount>1)><div id = "block${block}" class="hidden"></#if>
								<table id="report">									
									<tr><td style="border-width: 0px; border:none;">
											
											<#list testPathList as path>
											<#assign pathText=utils.getValueByPathFormula(req, path, testFormula)/>
											<hr/>
											<table>
												<tr>
													<td style="text-align:left; border: 0px;"><strong>Test: </strong></td>
													<td style="border: 0px;"><a onclick = 'if(typeof openEditor != "undefined"){if(openEditor("${path}")!=null)return false;}window.location.href="${path}";' href = "#">${pathText}</a></td>
												</tr>
												<#if (testCntList[iterate]>-1)>
												<tr>
													<td style="text-align:left; border: 0px;"><strong>Hits: </strong></td>
													<td style="border: 0px;">${testCntList[iterate]}</td>
												</tr>
												</#if>
																
												<#if coverage.hasErrors(childTestPurpose.getQualifiedId(),path)>
													<#local errors_names=coverage.getErrorNamesForNodeAndTest(childTestPurpose.getQualifiedId(),path)/>
													<#local errors_desc=coverage.getErrorDescriptionsForNodeAndTest(childTestPurpose.getQualifiedId(),path)/>
													<#local errors_links=coverage.getErrorLinksForNodeAndTest(childTestPurpose.getQualifiedId(),path)/>
												<tr>
													<td style="text-align:left; border: 0px;"><strong>Errors: </strong></td>
													<td style="border: 0px;">
													<ul style="margin: 0px;">
													<#list 0..errors_names?size-1 as i>
														<#local error_name=(errors_names[i])>
														<#local error_desc=errors_desc[i]>
														<#local error_link=errors_links[i]>  
														<li><div title='${error_desc}'>
															<#if error_link!="">
																<a class="error" style="color:black; text-decoration:none;" onclick = 'if(typeof openEditor != "undefined"){if(openEditor("${error_link}")!=null)return false;}window.location.href="${error_link}";' href = "#">${error_name}</a>
															<#else>${error_name}</#if>
														</div></li>
													</#list>
													</ul></td>
												</tr>
												</#if>
											</table>
											
											<#local iterate = iterate + 1/>
											</#list>
									</td></tr>
								</table>			
								<#if (testsCount>1)>
								</div>
								<@num block+1/>	
								</#if>														
							</#if>                		
						</td>
						<td>${childTestPurpose.attrs().getAttributeValue("_status", "")}</td>
					  </tr>
        			</#list>
        			</table>
        		 </#if>
        		<#list childrenList as child>
            		<#if coverage.isCoverable(child, context)>                		
            		<ul>
            			<li>
                			<@showreq child lvl+1/>
                		</li>
                	</ul>              
            		</#if> 
        		</#list>
   		 </#if>
    </div> 
</#macro>

<#macro statistic req>
	<#assign rev = statman.getArrayData(req, coverage)/>	
	<#assign hasTp = (rev["number of test purposes"]>0)/>
	<table id="measure">
		<tr><th>Measure</th><th>Requirements</th><#if hasTp><th>Test purposes</th></#if><th>Leaves</th><#if coverage.loadStorage()><th>Covered</th><th>Not covered</th></#if></tr>
			<tr>
				<td>Count</td>
				<td>${rev["number of requirements"]}</td>
				<#if hasTp><td>${rev["number of test purposes"]}</td></#if>
				<td>${rev["number of leaves"]}</td>
			<#if coverage.loadStorage()>
				<td>${rev["number coverage (green status)"]}</td>
				<td>${rev["number coverage (red status)"]}</td>
			</tr>									
			<tr>
				<td>Percent</td>
				<td>-</td>
				<#if hasTp><td>-</td></#if>
				<td>-</td>
				<td>${rev["percent coverage (green status)"]}%</td>
				<td>${rev["percent coverage (red status)"]}%</td>
			</#if>
			</tr>				
	</table>
</#macro>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>

<style type="text/css">
<#if fitImages?? >
  img:not(.base64),object{
    max-width: 100%;
    object-fit: contain;
  }
</#if>
.green {
	background: #bcee68;
}
.red {
	background: pink;
}

.gray {
	background: lightgray;
}
.yellow {
	background: #fff68f;
}

ul {
	list-style-type: none;
	margin-bottom: 10px;
	margin-left:-25px;
	margin-right:-10px;
}

a:link{
	color:#236b8e;
}

a:visited{
	color:#191970;
}

a:focus {
	color:#2f2f4f;
	text-shadow: 0px 0px 1px #2f2f4f, 1px 1px 2px #2f2f4f;
}

a:hover{
	text-shadow: 0px 0px 1px grey;
}

.block {
	width:auto;
	clear:both; 
	padding:10px; 
	background:rgba(184,186,198, 0.1); 
	border: outset 1px #1e90ff;
	border-top:none;
	border-left:dotted 1px #1e90ff; 
	border-radius: 0px 0px 5px 5px;
	box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
}

.hidden{
    height:0px;
    overflow: hidden;
}

.addtest{
	width: 100px;
	cursor: pointer; cursor: hand;
}

.error{	
	text-shadow: 0px 0px 1px red, 2px 2px 3px rgb(255, 182, 193);
}

.errortable{
   background: rgb(255, 182, 193);
   border-collapse:collapse;
   box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
}

.errortable td, th{
   font-size:1em;
   border:1px outset #FF69B4;
   padding:3px 7px 2px 7px;
   color:#000000;
}

.errortable th {
    text-align:center;
    padding-top:5px;
    padding-bottom:4px;
	background: rgb(204,204,204); /* Old browsers */
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
}

.report{
   width:100%;
   border-collapse:collapse;
}

.report td, th { 
   font-size:1em;
   border:1px solid #666666;
   color:#000000;
}

.report td{
   vertical-align: text-top;  
   text-align:left;
}

.report th {
    text-align:center;
    padding-top:5px;
    padding-bottom:4px;
	background: rgb(204,204,204); /* Old browsers */
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
}

#measure{
   background: white;
   border-collapse:collapse;
   box-shadow: 5px 5px 10px rgba(122, 122, 122, 0.5);
   margin: 25px;
}

#measure td, th{
   font-size:1em;
   border:1px outset black;
   padding:3px 7px 2px 7px;
   color:#000000;
}

#measure th {
    text-align:center;
    padding-top:5px;
    padding-bottom:4px;
	background: rgb(204,204,204); /* Old browsers */
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, rgba(238,238,238,1) 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%,rgba(238,238,238,1) 100%); /* W3C */
}

body{
	min-width: 560px;
}
	
#header {
	height: 70px;
	border-radius: 5px 5px 0 0;
	background: rgb(204,204,204); /* Old browsers */
	background: -moz-linear-gradient(top,  rgba(204,204,204,1) 0%, white 100%); /* FF3.6+ */
	background: linear-gradient(to bottom,  rgba(204,204,204,1) 0%, white 100%); /* W3C */
}

#header h1{
	font: bold 36px Arial serif;
	margin: 0px;
	padding:10px 10px 0 0;
	text-align: center;
	text-shadow: 1px 1px 0 #8E9FA5;
}
	
#footer {
    height: 30px;
    text-align: right;
    padding:10px 10px 0 0;
    border-radius: 0 0 5px 5px;
	background: -moz-linear-gradient(top,  white 0%, rgba(219,220,226,1) 54%, rgba(184,186,198,1) 97%, rgba(184,186,198,1) 97%); /* FF3.6+ */
	background: linear-gradient(to bottom,  white 0%,rgba(219,220,226,1) 54%,rgba(184,186,198,1) 97%,rgba(184,186,198,1) 97%); /* W3C */
}
<#include "../lib/comInfoCSS.ftl" />
<#include "../lib/projecttabscss.ftl" />
</style>

<script>
const png1 = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAYAAACNiR0NAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3wEMCzYiCCOtIgAAAIlJREFUOMvt080KAWEUxvEfZcFGYitpMgy5OpfCvaCUDOUOXIqlLLwLpslHZvn+l+c5nfM8nQ6RSCV0scbkTU+KLXpFoV7S3EYHK8xK9AGWaKD1rcsEG+wwfqpnOAVt/mv0BGfkYVAfexwKS16ofRiahug3NHHFApd/jjTEMTibVnX5DKP4AJEHd6dzEM+IXu5IAAAAAElFTkSuQmCC',
	png2 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAQAAAAngNWGAAAAAmJLR0QAAKqNIzIAAAAJcEhZcwAAAEgAAABIAEbJaz4AAABoSURBVCjP1c/NCkBAGIXhN1nasUXI+Mv1z9BEI8Ud2dpaqPmWnOXp6dSB/yUhk8GJhVECFROGRkJzdhy9hHasaBlNcTjqZxW8woiLgNC317AxU/jYgOVA+ViLxVD5b2hOCYOYUsK+mRuFlREpEzrazAAAACV0RVh0ZGF0ZTpjcmVhdGUAMjAxNS0wOS0wOFQwNzowNTozOS0wNDowMEXWUfsAAAAldEVYdGRhdGU6bW9kaWZ5ADIwMTUtMDktMDhUMDc6MDU6MzktMDQ6MDA0i+lHAAAAAElFTkSuQmCC",
	png3 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAYAAACNiR0NAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAACYktHRAAAqo0jMgAAACV0RVh0ZGF0ZTpjcmVhdGUAMjAxNS0wOS0wOFQwNzowNTozOS0wNDowMEXWUfsAAAAldEVYdGRhdGU6bW9kaWZ5ADIwMTUtMDktMDhUMDc6MDU6MzktMDQ6MDA0i+lHAAAAhklEQVQ4T+3QXQqCQBhG4cEt6K2KRmmFS3UBrsSCSKwgN+S959WIELTM2znw4MwInz/GZnu3xX5Yru+AGndEOpjLeV2n0rB8WJoWBXb97o8S6M0q+NAn3/DEBovKcMIDn/9OD7lA934eGuCKM0IdjDpCQxvEOviWBw2b+1cpSrj9zmZbkzEd1M4RHiv7IOMAAAAASUVORK5CYII=";

function get_png_image_src(ind){
	if(ind == 0){
 		return png2;
 	}
 	return png1; 
}

function toggle(id, i){	
	var g = document.getElementById(i);
	if(g.src==png1){
		g.src = png2;
	}else{
		g.src = png1;
	}
    var e = document.getElementById(id);
    var elems = e.getElementsByTagName('*');
    if (e.className == "hidden"){
      	e.className = "block";
    }else{
      	e.className = "hidden";
    }
    return true;
}		
		
function toggletest(id, i){
	var elms = document.getElementsByClassName(i);
	for (index = 0; index < elms.length; ++index) {
		var g = elms[index];
		if (g.nodeName == 'IMG'){
			if(g.src==png1){
				g.src = png3;
			}else{
				g.src = png1;
			}
		}
		if (g.nodeName == 'TEXT'){
			if(g.innerHTML=='Развернуть список тестов'){
				g.innerHTML = 'Свернуть список тестов';
			} else {
				g.innerHTML = 'Развернуть список тестов';
			}
		}
	}

    var e = document.getElementById(id);
    var elems = e.getElementsByTagName('*');
    if (e.className == "hidden"){
       	e.className = "visible";
        return true;
    } else {
      	e.className = "hidden";
        return true;
    }
    return false;
}
</script>
</head>

<body>

<div id="header"><h1>Requality Project</h1></div>
<#if testIdFormula?? && testIdFormula!="">
	<#global testFormula = testIdFormula/>
<#else>
	<#global testFormula = "path"/>
</#if>
<@showheader rootReq/>
${utils.initMainGeneration()}<#t/>
<#if coverageFilePath?? || coverageStorageType??>
	<#if coverage.loadStorage()>
	
		<#if noAddTestButtons?? && noAddTestButtons!="false">
			<#global showbuttons = false>
		<#else>
			<#global showbuttons = true>
		</#if>
		<@num 0/>
		<div style="padding: 0 10px 10px 0;">
			<@statistic rootReq/>
			<@showreq rootReq 1/>
		</div>
	<#else>
		<#if coverageFilePath??>
			Coverage information cannot be loaded from 	${coverageFilePath}. Errors: ${coverage.getLastErrors()}
		<#else>
			Coverage information cannot be loaded from chosen source (${coverageStorageType}). Errors: ${coverage.getLastErrors()}
		</#if>
	</#if>	
<#else>
	There is no source of coverage information.<br>
	If using Eclipse plugin please press 'Update Coverage Source'  button on report settings' properties.<br>
	If using console mode please set 'coverageFilePath' parameter as -coverageFilePath="path to coverage info file". 
</#if>

<div id="footer">${progress.getDateTime()}</div>

<!-- ${.now?iso_local} -->
</body>
</html>