if (typeof(voteroom) == "undefined") voteroom = {};

$(function() {
	var MSG = {
		WEBSOCKET_NOT_SUPPORTED: "ブラウザがWebSocketをサポートしていません。",
		UNIT_SECOND: "秒",
		UNIT_MINUTE: "分",
		UNIT_HOUR: "時間",
		UNIT_DAY: "日",
		VOTE_END: "投票 終了",
		TIMER_MSG: "残り {0}{1}",
		JUST: "キリ番",
		JUST_MSG: "{0}の{1}を押しました",
		format: function(fmt) {
			for (i = 1; i < arguments.length; i++) {
				var reg = new RegExp("\\{" + (i - 1) + "\\}", "g")
				fmt = fmt.replace(reg,arguments[i]);
			}
			return fmt;
		}
	};
	var DAY = 24 * 60 * 60,
		HOUR = 60 * 60,
		MINUTE = 60
	var debug = new Debugger($("#debug"));
	function Debugger($el, max) {
		var enabled = (location.hash == "#debug");
		max = max | 10;
		var cnt = 0;
		function log(msg) {
			if (enabled && $el.length) {
				cnt++;
				var $p = $("<p/>");
				$p.text(msg);
				
				if (cnt > max) {
					$el.find("p:first").remove();
				}
				$el.append($p);
			}
		}
		$.extend(this, {
			"log" : log
		});
	}
	function DummyTimer() {
		this.canVote = function() { return true;}
	}
	function Timer($el, time) {
		function calc() {
			var now = new Date(),
				rest = Math.floor((limit - now.getTime()) / 1000),
				next = 1,
				unit = MSG.UNIT_SECOND;
			if (rest <= 0) {
				$el.text(MSG.VOTE_END);
				return;
			} 
			if (rest > DAY) {
				next = rest % DAY;
				rest = Math.floor(rest / DAY);
				unit = MSG.UNIT_DAY;
			} else if (rest > HOUR) {
				next = rest % HOUR;
				rest = Math.floor(rest / HOUR);
				unit = MSG.UNIT_HOUR;
			} else if (rest > MINUTE) {
				next = rest % MINUTE;
				rest = Math.floor(rest / MINUTE);
				unit = MSG.UNIT_MINUTE;
			}
			$el.text(MSG.format(MSG.TIMER_MSG, rest, unit));
			setTimeout(calc, next * 1000);
		}
		function canVote() {
			return limit - Date.now() > 0;
		}
		var limit = Date.now() + (time * 1000);
		calc();
		$.extend(this, {
			"canVote" : canVote
		})
	}
	function isIOS() {
		var ua = navigator.userAgent.toLowerCase();
		if (ua.indexOf("iphone") != -1) return true;
		if (ua.indexOf("ipad") != -1) return true;
		if (ua.indexOf("ipod") != -1) return true;
		
		return false;
	}
	voteroom.VoteRoom = function(uri, clientId, timeLimit) {
		function createWebSocket() {
			if (!window.WebSocket) {
				$("#onError span").text(MSG.WEBSOCKET_NOT_SUPPORTED);
				$("#onError").show();
				return null;
			}
			debug.log("createWebSocket");
			var ret = new WebSocket(uri);
			ret.onopen = openEvent;
			ret.onmessage = receiveEvent;
			ret.onclose = closeEvent;
			return ret;
		}
		function receiveEvent(event) {
			var data = JSON.parse(event.data);
			
			// Handle errors
			if(data.error) {
				ws.onclose = null;
				ws.close();
				$("#onError span").text(data.error);
				$("#onError").show();
				return;
			}
			if (data.kind == "member") {
				$member.text(data.count);
			} else if (data.kind == "vote") {
				var $b = $("#num-" + data.key),
					n1 = parseInt(data.count),
					n2 = parseInt($b.text());
				if (n1 > n2) {
					$b.text(n1);
				}
			} else if (data.kind == clientId) {
				var $btn = $("#btn-" + data.key),
					$p = $("<p><span class='label label-success'></span><span></span></p>"),
					$span = $p.find("span");
				$($span[0]).css("background-color", $btn.attr("data-color")).text(MSG.JUST);
				$($span[1]).text(MSG.format(MSG.JUST_MSG, $btn.find("div:eq(0)").text(), data.count));
				$("#message").append($p);
			}
		}
		function openEvent(evt) {
			debug.log("open: " + retryCount);
			retryCount = 0;
			setTimeout(function() {
				ws.send("###member###");
			}, 200);
		}
		function closeEvent(evt) {
			debug.log("close: " + retryCount)
			if (retryCount > MAX_RETRY_COUNT) {
				$("#onError span").text("Connection interrupted!")
				$("#onError").show()
			} else {
				var next = RETRY_INTERVAL_BASE * retryCount * 1000;
				setTimeout(function() {
					ws = createWebSocket();
				}, next)
			}
			retryCount++;
		}
		function clickEvent(evt) {
			if (!timer.canVote()) {
				return;
			}
			var b = $(this),
				key = b.attr("data-key");
			b.css("background-color", "#ccc");
			setTimeout(function() {
				b.css("background-color", b.attr("data-color"));
			}, 80);
			
			if (!ws || ws.readyState != 1) {
				ws = createWebSocket();
			}
			ws.send(key);
			cnt++;
			$yours.text(cnt);
		}
		window.onunload = function() {
			if (ws) {
				ws.onclose = null;
			}
		}
		if (isIOS()) {
			$(".vote").on("touchstart", clickEvent);
		} else {
			$(".vote").click(clickEvent);
		}
		var MAX_RETRY_COUNT = 5,
		    RETRY_INTERVAL_BASE = 5,
		    cnt = 0,
		    retryCount = 0,
		    $member = $("#member"),
		    $yours = $("#yours"),
		    ws = createWebSocket(),
			timer = timeLimit < 0 ?
				new DummyTimer() : 
				new Timer($("#timeLimit"), timeLimit);
		if (location.hash == "#debug") {
			$("#debug").show();
		}
	}
})
