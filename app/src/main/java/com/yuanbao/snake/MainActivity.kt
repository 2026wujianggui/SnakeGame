package com.yuanbao.snake

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var game: SnakeView
    private lateinit var tvScore: TextView
    private lateinit var tvBest: TextView
    private lateinit var btnMain: Button
    private lateinit var btnRestart: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        game = findViewById(R.id.game)
        tvScore = findViewById(R.id.tvScore)
        tvBest = findViewById(R.id.tvBest)
        btnMain = findViewById(R.id.btnMain)
        btnRestart = findViewById(R.id.btnRestart)

        game.onScoreChanged = { score, best ->
            tvScore.text = score.toString()
            tvBest.text = best.toString()
        }

        game.onStateChanged = { state ->
            btnMain.text = when (state) {
                SnakeView.State.RUNNING -> "暂停"
                SnakeView.State.PAUSED -> "继续"
                SnakeView.State.OVER -> "开始"
                SnakeView.State.READY -> "开始"
            }
        }

        btnMain.setOnClickListener { game.toggle() }
        btnRestart.setOnClickListener { game.reset(); game.start() }

        findViewById<View>(R.id.btnUp).setOnClickListener { game.turn(SnakeView.Point(0, -1)) }
        findViewById<View>(R.id.btnDown).setOnClickListener { game.turn(SnakeView.Point(0, 1)) }
        findViewById<View>(R.id.btnLeft).setOnClickListener { game.turn(SnakeView.Point(-1, 0)) }
        findViewById<View>(R.id.btnRight).setOnClickListener { game.turn(SnakeView.Point(1, 0)) }
    }

    override fun onPause() {
        super.onPause()
        game.pause()
    }
}
