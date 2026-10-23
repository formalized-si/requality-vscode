
.pttab {
    display: -webkit-box;
    display: -webkit-flex;
    display: -ms-flexbox;
    border: 1px solid #000;
    background-color: #f1f1f1;
    display: flex;
}
<#assign width=""/>
<#if TabHeaderWidth??>
<#assign width="width: "+(TabHeaderWidth!"")+";"/>
</#if>
.pt {
    background-color: inherit;
    border: none;
    outline: none;
    cursor: pointer;
    padding: 14px 16px;
    transition: 0.3s;
    
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    flex: 1;
    //fallback
    max-width: 100%;
    display:inline-block;  
    ${width}
}
.ptactive{
	font-weight: bold;
}
.pttab .ptactive {
    background-color: #ccc;
}
.pttabcontent {
    padding: 6px 12px;
    border: 1px solid #ccc;
    border-top: none;
}