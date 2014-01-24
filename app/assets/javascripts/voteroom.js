if (typeof(voteroom) == "undefined") voteroom = {};

$(function() {
	voteroom.VoteRoom = function(uri) {
		function createWebSocket() {
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
				$("#num-" + data.key).text(data.count);
			} else {
				console.log("Unknown event: " + event.data);
			}
		}
		function openEvent(evt) {
			console.log("open: " + retryCount);
			ws.send("###member###");
			retryCount = 0;
		}
		function closeEvent(evt) {
			console.log("close: " + retryCount)
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
		window.onunload = function() {
			if (ws) {
				ws.onclose = null;
			}
		}
		$(".vote").click(function() {
			cnt++;
			$yours.text(cnt);
			ws.send($(this).attr("data-key"));
		});
		var MAX_RETRY_COUNT = 5,
		    RETRY_INTERVAL_BASE = 5,
		    cnt = 0,
		    $member = $("#member"),
		    $yours = $("#yours"),
		    ws = createWebSocket();
		setInterval(function() {
			if (ws) ws.send("###member###");
		}, 25000);
	}
})
