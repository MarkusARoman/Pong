package com.lurch;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

// Lurch v.0.1 Engine Imports (file paths may vary).
import com.lurch.core.Window;
import com.lurch.core.Shader;
import com.lurch.time.TimeBuffer;
import com.lurch.display.Quad;

public class Main {
    private final Window window;
    private final Shader shader;
    private final TimeBuffer timeBuffer;

    private final Ball ball;
    private final Paddle leftPaddle;
    private final Paddle rightPaddle;

    private final float VP = 0.025f; // Virtual Pixel
    private boolean isGameRunning = false; // Tracks game state
    private int leftScore = 0; // Score for left player
    private int rightScore = 0; // Score for right player
    private final int maxScore = 11; // Game ends at 11 points

    public Main() 
    {
        this.window = new Window("Pong");
        window.create();

        this.shader = new Shader("color");
        shader.create();

        this.timeBuffer = new TimeBuffer(60);
        timeBuffer.create();

        shader.bind();
        Matrix4f proj = window.getOrtho();
        shader.setUniformMatrix4f("u_projection", proj);
        shader.setUniform4f("u_color", 1, 1, 1, 1);
        shader.unbind();

        this.ball = new Ball(new Vector2f(0, 0), new Vector2f(1*VP, 1*VP), new Vector2f(15*VP, 0));
        this.leftPaddle = new Paddle(new Vector2f(-0.9f, 0f), new Vector2f(0.5f*VP, 4f*VP), GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_S);
        this.rightPaddle = new Paddle(new Vector2f(0.9f, 0), new Vector2f(0.5f*VP, 4f*VP), GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN);
    }

    public void run() 
    {
        loop();
        free();
    }

    private void loop() 
    {
        while (!window.shouldClose()) 
        {
            window.clear();
            timeBuffer.refresh();

            handleInput();
            
            if (isGameRunning) {
                int pendingUpdates = timeBuffer.getPendingUpdates();
                for (int i = 0; i < pendingUpdates; i++) {
                    ball.update(1f / 60f);
                    leftPaddle.update(1f / 60f);
                    rightPaddle.update(1f / 60f);
                    checkCollision();
                    checkBallOutOfBounds();
                }
            }

            shader.bind();
            ball.render(shader);
            leftPaddle.render(shader);
            rightPaddle.render(shader);
            shader.unbind();
            // Note: Score display would require additional rendering logic (e.g., text rendering)
            window.refresh();
        }
    }

    private void handleInput() 
    {
        if (window.isKeyPressed(GLFW.GLFW_KEY_SPACE) && !isGameRunning) {
            isGameRunning = true;
            ball.reset();
            timeBuffer.reset();
        }
        leftPaddle.handleInput(window);
        rightPaddle.handleInput(window);
    }

    private void checkCollision() 
    {
        if (ball.getAABB().intersects(leftPaddle.getAABB())) 
        {
            ball.resolveCollision(leftPaddle);
        } 
        else if (ball.getAABB().intersects(rightPaddle.getAABB())) 
        {
            ball.resolveCollision(rightPaddle);
        }
    }

    private void checkBallOutOfBounds() 
    {
        if (ball.getPosition().x + ball.getSize().x > 1.0f) {
            leftScore++;
            checkGameOver();
            isGameRunning = false;
            ball.reset();
            timeBuffer.reset();
        } else if (ball.getPosition().x - ball.getSize().x < -1.0f) {
            rightScore++;
            checkGameOver();
            isGameRunning = false;
            ball.reset();
            timeBuffer.reset();
        }
    }

    private void checkGameOver() 
    {
        if (leftScore >= maxScore || rightScore >= maxScore) {
            isGameRunning = false;
            leftScore = 0;
            rightScore = 0;

            // Note: Game over screen not included. Additional rendering logic required.
        }
    }

    private void free() 
    {
        window.delete();
        shader.delete();
        ball.delete();
        leftPaddle.delete();
        rightPaddle.delete();
    }

    public static void main(String[] args) 
    {
        new Main().run();
    }

    public class AABB 
    {
        public final Vector2f position;
        public final Vector2f halfSize;

        public AABB(Vector2f position, Vector2f halfSize) {
            this.position = position;
            this.halfSize = halfSize;
        }

        public boolean intersects(AABB other) {
            return 
                Math.abs(position.x - other.position.x) < (halfSize.x + other.halfSize.x) &&
                Math.abs(position.y - other.position.y) < (halfSize.y + other.halfSize.y);
        }
    }

    class Ball 
    {
        private final Quad quad;
        private final Vector2f position;
        private final Vector2f size;
        private final Vector2f velocity;
        private float speed = 15 * VP;

        public Ball(Vector2f position, Vector2f size, Vector2f velocity) 
        {
            this.quad = new Quad();
            this.quad.create();
            this.position = position;
            this.size = size;
            this.velocity = velocity;
        }

        public void update(float dt) 
        {
            position.add(new Vector2f(velocity).mul(dt));

            if (position.y + size.y > 1.0f || position.y - size.y < -1.0f) 
            {
                velocity.y = -velocity.y;
            }
        }

        public void resolveCollision(Paddle paddle) 
        {
            Vector2f paddlePos = paddle.getPosition();
            Vector2f paddleSize = paddle.getSize();

            // Calculate hit position relative to paddle center
            float hitPos = (position.y - paddlePos.y) / paddleSize.y;

            // Map hit position to angle between -45 and 45 degrees
            float maxAngle = (float) Math.toRadians(45);
            float angle = hitPos * maxAngle;

            // Adjust speed and set new velocity
            speed *= 1.1f; // Increase speed on hit
            velocity.x = (float) (speed * Math.cos(angle)) * (velocity.x > 0 ? -1 : 1);
            velocity.y = (float) (speed * Math.sin(angle));

            // Prevent ball from getting stuck in paddle
            if (position.x < paddlePos.x) {
                position.x = paddlePos.x - paddleSize.x - size.x;
            } else {
                position.x = paddlePos.x + paddleSize.x + size.x;
            }
        }

        public void reset() 
        {
            position.set(0, 0);
            speed = 15 * VP;

            // Randomize initial direction
            velocity.set((Math.random() > 0.5 ? 1 : -1) * speed, 0);
        }

        public void render(Shader shader) 
        {
            Matrix4f transform = new Matrix4f().translate(position.x, position.y, 0)
                                           .scale(size.x, size.y, 1);
            shader.setUniformMatrix4f("u_transform", transform);
            quad.render();
        }

        public AABB getAABB() 
        {
            return new AABB(position, size);
        }

        public Vector2f getPosition() 
        {
            return position;
        }

        public Vector2f getSize() 
        {
            return size;
        }

        public void delete() 
        {
            quad.delete();
        }
    }

    class Paddle 
    {
        private final Quad quad;
        private final Vector2f position;
        private final Vector2f size;
        private final int upKey;
        private final int downKey;

        public Paddle(Vector2f position, Vector2f size, int upKey, int downKey) 
        {
            this.quad = new Quad();
            this.quad.create();
            this.position = position;
            this.size = size;
            this.upKey = upKey;
            this.downKey = downKey;
        }

        public void handleInput(Window window) 
        {
            float speed = 0.02f;
            if (window.isKeyPressed(upKey)) 
            {
                position.y += speed;
            }
            if (window.isKeyPressed(downKey)) 
            {
                position.y -= speed;
            }
        }

        public void update(float dt) 
        {
            if (position.y + size.y > 1.0f) {
                position.y = 1.0f - size.y;
            } else if (position.y - size.y < -1.0f) {
                position.y = -1.0f + size.y;
            }
        }

        public void render(Shader shader) 
        {
            Matrix4f transform = new Matrix4f().translate(position.x, position.y, 0)
                                           .scale(size.x, size.y, 1);
            shader.setUniformMatrix4f("u_transform", transform);
            quad.render();
        }

        public AABB getAABB() 
        {
            return new AABB(position, size);
        }

        public Vector2f getPosition() 
        {
            return position;
        }

        public Vector2f getSize() 
        {
            return size;
        }

        public void delete() 
        {
            quad.delete();
        }
    }
}
