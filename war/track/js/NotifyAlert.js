// ----------------------------------------------------------------------------
// Copyright 2007-2014, OpenGTS
// All rights reserved
// ----------------------------------------------------------------------------
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
// http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// ----------------------------------------------------------------------------
// ----------------------------------------------------------------------------

/* imported vars */
// RULE_NAME                alert
// RULE_EVAL_URL            ./Track?page=RULE_EVAL&rule=alert
// RuleRefreshInterval      
// RuleRefreshCount         
// RuleRefreshTimer         
// BIND_TO_PARENT"          
// TEXT_Refresh             
// TEXT_Alert               
// TEXT_Alert_Off           
// TEXT_Alert_On            
// TEXT_No_Alerts           
// TEXT_Goto_Alerts         
// TEXT_Seconds_To_Update   
// TEXT_Please_Login        
// TEXT_Session_Expired     
// TEXT_Opener_Closed       
// URL_Alert_Page           
// SOUND_URL_Off            
// SOUND_URL_On             
// SOUND_URL_On_LOOP        false

// "Result" XML tag
var TAG_Result                  = "Result";

var ID_ALERT_IMAGE              = "alertImage";
var ID_ALERT_STATE_TEXT         = "alertStateText";
var ID_ALERT_ACTION_TEXT        = "alertActionText";
var ID_ALERT_ACTION_SOUND       = "alertActionSound";
var ID_ALERT_POLL_INTERVAL      = "alertPollInterval";

var CSS_ALERT_TABLE             = "alertTable";
var CSS_ALERT_IMAGE             = "alertImage";        // on/off
var CSS_ALERT_STATE_TEXT        = "alertStateText";    // on/off
var CSS_ALERT_ACTION_TEXT       = "alertActionText";   // on/off
var CSS_ALERT_POLL_INTERVAL     = "alertPollInterval";
var CSS_ALERT_RULE_NAME         = "alertRuleName";     

var globalMatchState            = 0; // false
var globalAlertCount            = 0;

// ----------------------------------------------------------------------------

function ruleAlertOnLoad()
{
    ruleStartRefreshTimer();
};

// ----------------------------------------------------------------------------

function ruleCreateWindow(url)
{
    openFixedWindow(url, "AlertPanel", 410, 140);
}

// ----------------------------------------------------------------------------

/* sets the AlertRefreshButton button text */
var _alertPollIntervalText = null;
function _ruleSetPollIntervalText(count)
{
    if (_alertPollIntervalText == null) {
        _alertPollIntervalText = document.getElementById(CSS_ALERT_POLL_INTERVAL);
    }
    if (_alertPollIntervalText != null) {
        _alertPollIntervalText.innerHTML = count + " " + TEXT_Seconds_To_Update;
    }
};

// ----------------------------------------------------------------------------

var MIN_REFRESH_INTERVAL = 10;

function ruleStartRefreshTimer() 
{
    if (RuleRefreshInterval > 0) {
        //RuleRefreshCount = (RuleRefreshInterval > MIN_REFRESH_INTERVAL)? 
        //    RuleRefreshInterval : MIN_REFRESH_INTERVAL;
        RuleRefreshCount = 0; 
        RuleRefreshTimer = setInterval('_ruleTimerRefresh()',1000); // setTimeout
        _ruleSetPollIntervalText(RuleRefreshCount);
    } else {
        _ruleSetPollIntervalText(0);
    }
};

// ----------------------------------------------------------------------------

/* periodic map update timer target */
function _ruleTimerRefresh() 
{
    
    /* close if opener is null */
    if (BIND_TO_PARENT && (opener == null)) {
        window.close();
        return;
    }
    
    /* time for rule trigger check */
    if (--RuleRefreshCount <= 0) {
        // timer expired
        _ruleCheckRuleTrigger();
        RuleRefreshCount = (RuleRefreshInterval > MIN_REFRESH_INTERVAL)?  // start over
            RuleRefreshInterval : MIN_REFRESH_INTERVAL;
    }
    
    /* reset displayed counter */
    _ruleSetPollIntervalText(RuleRefreshCount);
    
};

var _ruleCheckInprocess = false;
function _ruleCheckRuleTrigger()
{

    /* get the latitude/longitude for the zip */
    var url = RULE_EVAL_URL;
    try {
        var req = getXMLHttpRequest();
        if (req) {
            _ruleCheckInprocess = true;
            req.open("GET", url, true);
            //req.setRequestHeader("CACHE-CONTROL", "NO-CACHE");
            //req.setRequestHeader("PRAGMA", "NO-CACHE");
            //req.setRequestHeader("If-Modified-Since", "Sat, 1 Jan 2000 00:00:00 GMT");
            req.onreadystatechange = function() {
                if (req.readyState == 4) {
                    var xmlStr = req.responseText.trim();
                    // "<Result>1</Result>"
                    // "<Result>0</Result>"
                    //alert('Response: ' + xmlStr);
                    _ruleCheckInprocess = false;
                    _ruleParseResponse(xmlStr);
                } else
                if (req.readyState == 1) {
                    // alert('Loading from URL: [' + req.readyState + ']\n' + url);
                } else {
                    // alert('Problem loading URL? [' + req.readyState + ']\n' + url);
                    _ruleCheckInprocess = false;
                }
            }
            req.send(null);
        } else {
            alert("Error [_ruleCheckRuleTrigger]:\n" + url);
        }
    } catch (e) {
        alert("Error [_ruleCheckRuleTrigger]:\n" + e);
    }

};

function _ruleParseResponse(xmlStr)
{

    /* create XML doc */
    var xmlDoc = createXMLDocument(xmlStr);
    if (xmlDoc == null) {
        return false;
    }

    /* find top XML tag */
    var matchState = -1;
    var result = xmlDoc.getElementsByTagName(TAG_Result);
    if (result.length > 0) {
        var resultElem = result[0];
        var resultAttr = resultElem.attributes;
        var resultVal  = resultElem.childNodes[0]? resultElem.childNodes[0].nodeValue : "";
        if ((resultVal == "") || (resultVal == "0") || (xmlStr == "false") || (xmlStr == "FALSE")) {
            matchState = 0;
        } else {
            matchState = 1;
        }
    } else {
        matchState = -1;
    }

    /* (re)set button text */
    var imageTD  = document.getElementById(ID_ALERT_IMAGE);
    var stateTD  = document.getElementById(ID_ALERT_STATE_TEXT);
    var textTD   = document.getElementById(ID_ALERT_ACTION_TEXT);
    var soundTD  = document.getElementById(ID_ALERT_ACTION_SOUND);
    if (matchState < 0) {
        // login expired
        if (imageTD) {
            imageTD.className  = CSS_ALERT_IMAGE + "_login";
        }
        if (stateTD) {
            stateTD.innerHTML  = TEXT_Please_Login;
            stateTD.className  = CSS_ALERT_STATE_TEXT + "_login";
        }
        if (textTD) {
            if (BIND_TO_PARENT && (opener == null)) {
                textTD.innerHTML = TEXT_Opener_Closed;
            } else {
                textTD.innerHTML = TEXT_Session_Expired;
            }
            textTD.className = CSS_ALERT_ACTION_TEXT + "_login";
        }
        if (soundTD) {
            soundTD.innerHTML = "";
        }
        if (RuleRefreshTimer != null) {
            clearInterval(RuleRefreshTimer);
            RuleRefreshTimer = null;
        }
    } else
    if (matchState != globalMatchState) {
        // state change
        var isMatch = (matchState == 1);
        if (imageTD) {
            if (isMatch) {
                imageTD.className  = CSS_ALERT_IMAGE + "_on";
            } else {
                imageTD.className  = CSS_ALERT_IMAGE + "_off";
            }
        }
        if (stateTD) {
            var html;
            var clsn;
            if (isMatch) {
                html = TEXT_Alert_On;
                clsn = CSS_ALERT_STATE_TEXT + "_on";
            } else {
                html = TEXT_Alert_Off;
                clsn = CSS_ALERT_STATE_TEXT + "_off";
            }
            html += " <span class='" + CSS_ALERT_RULE_NAME + "'>(" + RULE_NAME + ")</span>";
            stateTD.innerHTML = html;
            stateTD.className = clsn;
        }
        if (textTD) {
            if (isMatch) {
                // <span onclick="parent.main.location='url'">...</span>
                if (URL_Alert_Page != "") {
                    var link = "";
                    link += "<a onclick=\"javascript:_runGotoAlertPage();\">";
                    link += TEXT_Goto_Alerts;
                    link += "</a>";
                    textTD.innerHTML = link;
                } else {
                    textTD.innerHTML = TEXT_Goto_Alerts;
                }
                textTD.className = CSS_ALERT_ACTION_TEXT + "_on";
            } else {
                textTD.innerHTML = TEXT_No_Alerts;
                textTD.className = CSS_ALERT_ACTION_TEXT + "_off";
            }
        }
        if (soundTD) {
            if (isMatch) {
                if (SOUND_URL_On && (SOUND_URL_On != "")) {
                    var loop = (SOUND_URL_On_LOOP)? "true" : "false";
                    soundTD.innerHTML = "<embed src='" + SOUND_URL_On  + "' hidden='true' autostart='true' loop='"+loop+"'/>";
                } else {
                    soundTD.innerHTML = "";
                }
            } else {
                if (SOUND_URL_Off && (SOUND_URL_Off != "")) {
                    soundTD.innerHTML = "<embed src='" + SOUND_URL_Off + "' hidden='true' autostart='true' loop='false'/>";
                } else {
                    soundTD.innerHTML = "";
                }
            }
        }
        globalMatchState = matchState;
        globalAlertCount = 0; // even if 'isMatch', start with '0'
    } else
    if (matchState == 1) { // and (globalMatchState == 1)
        // alarm remains on, replay sound?
        globalAlertCount++;
        if (!SOUND_URL_On_LOOP && ((globalAlertCount % 3) == 0) && SOUND_URL_On && (SOUND_URL_On != "")) {
            soundTD.innerHTML = "<embed src='" + SOUND_URL_On  + "' hidden='true' autostart='true' loop='false'/>";
        }
    }

}

function _runGotoAlertPage()
{
    if (BIND_TO_PARENT) {
        if (opener) {
            opener.location.href = URL_Alert_Page;
        } else {
            openURL(URL_Alert_Page, "AlertPage");
        }
    } else {
        // open separate window always
        openResizableWindow(URL_Alert_Page, "AlertPage", 0, 0);
    }
}

// ----------------------------------------------------------------------------
