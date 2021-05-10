package com.jeanwest.reader;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class loginActivity extends AppCompatActivity {

    public EditText password;
    public TextView feedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        password = (EditText) findViewById(R.id.passwordText);
        feedback = (TextView) findViewById(R.id.feedback);
    }

    public void loginButton(View view) {
        if(password.getText().toString().equals("123456")) {
            Intent intent = new Intent(this, advanceSetting.class);
            startActivity(intent);
        }
        else{
            feedback.setText("رمز عبور اشتباه است!");
            feedback.setTextColor(Color.RED);
        }
    }
}