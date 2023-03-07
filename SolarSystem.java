import java.nio.*;
import javax.swing.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.Math;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.*;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.*;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import com.jogamp.common.nio.Buffers;
import org.joml.*;
import java.util.*;

/**
 * This program takes in a .sol file, interprets it, and creates and renders the
 * planetary orbits and moons based on the given inputs
 */
public class SolarSystem extends JFrame implements GLEventListener {
    // Window set up
    private GL4 gl;
    private GLCanvas glCanvas; // Initialize canvas

    private static final int WINDOW_WIDTH = 1000;
    private static final int WINDOW_HEIGHT = 600;
    private static final String WINDOW_TITLE = "Solar System";
    private static final String VERTEX_SHADER_FILE = "solarSystem-vertex.glsl";
    private static final String FRAGMENT_SHADER_FILE = "solarSystem-fragment.glsl";

    // Shader
    private int renderingProgram; // Shader Id
    // private int m_matrixID;
    // private int v_matrixID;
    private int mv_matrixID;
    private int p_matrixID;
    private int n_matrixID;

    // Matrix Management
    private Matrix4f viewMatrix; // Stores view matrix
    private Matrix4f perspectiveMatrix = new Matrix4f(); // Stores perspective matrix
    private float aspectRatio;

    // Model Matrices
    private Matrix4f modelViewMatrix = new Matrix4f(); // Stores model matrix

    // Initialize scratch buffer in order to pass matrices to the gpu/shaders
    private final FloatBuffer scratchBuffer = Buffers.newDirectFloatBuffer(16);

    private int[] vao = new int[1];
    private int[] vbo = new int[4]; // TODO Add or subtract buffers as needed

    // Light Management;
    private float[] lightPos = new float[] { 0, 0, 0 };

    // Time Management
    private long startTime;
    private float timeElapsed; // In Millis
    private float timeOrbitAmount;
    private double amountOfOrbitalRotation;
    private double amountOfObjectRotation;

    // Camera Management
    private float cameraPosXYZ[];

    // Sol file Management
    private String solFileName;
    private File solFile;

    // Sphere atributes
    private int sunRGB[];
    private float sunADS[];
    private float solarAttenuation;
    private StellarSystem stellarSystem;
    private ImportedModel planetModel;
    private int numObjVertices;

    // Temp Objects
    private Matrix4f tempTranslation;
    private Matrix4f tempRotation;

    /**
     * Main method for program. Process arguments and make call to
     * constructor.
     * 
     * @param args
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.exit(0);
        }
        new SolarSystem(args[0]); // Change function call here
    }

    /**
     * Constructor for program. Set initial window parameters
     * and begin animation
     */
    public SolarSystem(String solFile) {
        setTitle(WINDOW_TITLE);
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        glCanvas = new GLCanvas();
        glCanvas.addGLEventListener(this);
        this.add(glCanvas);
        this.setVisible(true);
        setLocationRelativeTo(null);
        this.solFileName = solFile;

        Animator animator = new Animator(glCanvas);
        animator.start();

    }

    /*
     * Initialize matrices and load models as well as all other necessary
     * pre-computation.
     */
    @Override
    public void init(GLAutoDrawable arg0) {

        // Set up window
        this.gl = (GL4) GLContext.getCurrentGL();

        renderingProgram = Utils.createShaderProgram(VERTEX_SHADER_FILE, FRAGMENT_SHADER_FILE); // Ready the program.

        setDefaultCloseOperation(EXIT_ON_CLOSE); // Set shutdown condition on close
        // Initialize sun attributes
        this.cameraPosXYZ = new float[3];
        this.sunRGB = new int[3];
        this.sunADS = new float[3];
        this.solarAttenuation = 0;

        try {
            readSolFile(this.solFileName);
        } catch (Exception e) {
            System.exit(0);
        }

        initalizeModels();

        // Enable back faced culling
        gl.glEnable(GL_CULL_FACE);

        // Initialize Matrices
        this.viewMatrix = new Matrix4f().setLookAt(cameraPosXYZ[0], cameraPosXYZ[1], cameraPosXYZ[2], 0, 0, 0, 0, 1, 0);
        this.viewMatrix.scale(this.stellarSystem.sun.radius);

        this.mv_matrixID = gl.glGetUniformLocation(renderingProgram, "mv_matrix");
        this.p_matrixID = gl.glGetUniformLocation(renderingProgram, "p_matrix"); // Save model matrix id

        // Initialize Z buffers
        this.gl.glEnable(GL_DEPTH_TEST);
        this.gl.glDepthFunc(GL_LEQUAL);

        // Initialize lighting
        // installLights();

        this.startTime = System.currentTimeMillis();
    }

    /*
     * Prepare models and matrices to be drawn.
     */
    @Override
    public void display(GLAutoDrawable arg0) {
        // Clear screen and Z buffer
        this.gl.glClear(GL_COLOR_BUFFER_BIT); // clear screen
        this.gl.glClear(GL_DEPTH_BUFFER_BIT); // clear Z-buffer
        this.gl.glUseProgram(renderingProgram); // Shader Id to use
        this.gl.glClearColor(0f, 0f, 0f, 1f); // Black back ground.

        // Time calculations
        this.timeElapsed = (System.currentTimeMillis() - startTime) / 1000f;

        // Load and draw sun

        // Load the texture st values into the shader (same for all)
        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        this.gl.glVertexAttribPointer(1, 2, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(1);

        // Draw Sun
        // Create model view matrix for the sun to ensure it is scaled to the correct
        // size
        this.stellarSystem.sun.updateSunModelMatrix();
        this.viewMatrix.mul(this.stellarSystem.sun.modelMatrix, modelViewMatrix);
        // Bind the texture coordinates to vbo 1

        // Bind the texture
        this.gl.glActiveTexture(GL_TEXTURE0);
        this.gl.glBindTexture(GL_TEXTURE_2D, this.stellarSystem.sun.textureID);

        // Prepare for the transfer of data
        this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));
        this.gl.glUniformMatrix4fv(p_matrixID, 1, false, perspectiveMatrix.get(scratchBuffer));

        // Bind the shape vertices
        this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
        this.gl.glEnableVertexAttribArray(0);

        // Draw the shape!
        gl.glDrawArrays(GL_TRIANGLES, 0, planetModel.getNumVertices());

        // Draw each planet and each planets moons
        for (PlanetSystem planetSystem : this.stellarSystem.planets) {

            planetSystem.planet.updateObjectModelMatrix(null);
            this.viewMatrix.mul(planetSystem.planet.modelMatrix, this.modelViewMatrix);

            // Bind the texture
            this.gl.glActiveTexture(GL_TEXTURE0);
            this.gl.glBindTexture(GL_TEXTURE_2D, planetSystem.planet.textureID);

            // Get perspective matrix
            this.gl.glUniformMatrix4fv(p_matrixID, 1, false, perspectiveMatrix.get(scratchBuffer));
            this.gl.glUniformMatrix4fv(mv_matrixID, 1, false, modelViewMatrix.get(scratchBuffer));

            // Bind the shape vertices
            this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
            this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
            this.gl.glEnableVertexAttribArray(0);

            // Draw the shape!
            gl.glDrawArrays(GL_TRIANGLES, 0, planetModel.getNumVertices());

            // Draw each moon and adjust for planetary movement
            for (CelestialObject moon : planetSystem.moons) {

                moon.updateObjectModelMatrix(planetSystem.planet.translate);

                this.viewMatrix.mul(moon.modelMatrix, this.modelViewMatrix);

                // Bind the texture
                this.gl.glActiveTexture(GL_TEXTURE0);
                this.gl.glBindTexture(GL_TEXTURE_2D, moon.textureID);

                // Prepare for the transfer of data
                this.gl.glUniformMatrix4fv(mv_matrixID, 1, false,
                        modelViewMatrix.get(scratchBuffer));
                this.gl.glUniformMatrix4fv(p_matrixID, 1, false,
                        perspectiveMatrix.get(scratchBuffer));

                // Bind the shape vertices
                this.gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
                this.gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);
                this.gl.glEnableVertexAttribArray(0);

                // Draw the shape!
                gl.glDrawArrays(GL_TRIANGLES, 0, planetModel.getNumVertices());
            }
        }
    }

    /*
     * Upon resize event change the perspective matrix to relect the new aspect
     * ratio of the screen.
     */
    @Override
    public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {
        aspectRatio = (float) glCanvas.getWidth() / (float) glCanvas.getHeight(); // Get new aspect ratio
        // Set new perspective
        perspectiveMatrix.identity().perspective((float) Math.toRadians(60.0f), aspectRatio, 0.1f, 1000.0f);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.jogamp.opengl.GLEventListener#dispose(com.jogamp.opengl.GLAutoDrawable)
     */
    @Override
    public void dispose(GLAutoDrawable arg0) {
        // TODO Auto-generated method stub
    }

    /**
     * This method makes use of a buffered reader in order to parse the .sol file in
     * order to convert it into the planetary class system
     * 
     * @param solFileName
     */
    public void readSolFile(String solFileName) {
        String line;
        try {
            this.solFile = locateFile(solFileName);

            // Create buffer reader to read file input
            FileReader fr = new FileReader(solFile);
            BufferedReader br = new BufferedReader(fr);

            // Get first line of file
            line = br.readLine();
            String[] splitString = line.split("\t");

            // Set Camera location
            if (splitString.length != 3) {
                br.close();
                throw new Exception("Sol file contains the incorrect number of camera location coordinates. Needs 3");
            }

            // Set camera position
            for (int i = 0; i < 3; i++) {
                this.cameraPosXYZ[i] = Float.parseFloat(splitString[i]);
            }

            // Get second line of file
            line = br.readLine();
            splitString = line.split("\t");
            if (splitString.length != 7) {
                br.close();
                throw new Exception("Sol file contains the incorrect number of sun light attributes. Needs 7");
            }

            // Set the light attributes
            for (int i = 0; i < 7; i++) {
                if (i < 3) {
                    this.sunRGB[i] = Integer.parseInt(splitString[i]);
                } else if (i < 6) {
                    this.sunADS[i - 3] = Float.parseFloat(splitString[i]);
                } else {
                    this.solarAttenuation = Float.parseFloat(splitString[i]);
                }
            }

            // Get third line of the file
            line = br.readLine();
            splitString = line.split("\t");
            if (splitString.length != 3) {
                br.close();
                throw new Exception("Sol file contains the incorrect number of sun attribute. Needs 3");
            }

            // Create the sun object in the stellar system
            this.stellarSystem = new StellarSystem(
                    new CelestialObject(splitString[0], Float.parseFloat(splitString[1]),
                            Float.parseFloat(splitString[2])));
            int currentPlanet = -1; // Initial Planet Count

            while ((line = br.readLine()) != null && line.length() != 0) {
                // Split up input
                splitString = line.split("\t");
                // Check if the celestial body is a planet or moon
                if (splitString.length == 7) {
                    currentPlanet++;
                    stellarSystem.addPlanetSystem(new PlanetSystem(new CelestialObject(
                            splitString[1],
                            Float.parseFloat(splitString[2]),
                            Float.parseFloat(splitString[3]),
                            Float.parseFloat(splitString[4]),
                            Float.parseFloat(splitString[5]),
                            Float.parseFloat(splitString[6]))));
                } else if (splitString.length == 8) { // Add moons if planet has moons
                    stellarSystem.planets.get(currentPlanet).addMoon(new CelestialObject(
                            splitString[2],
                            Float.parseFloat(splitString[3]),
                            Float.parseFloat(splitString[4]),
                            Float.parseFloat(splitString[5]),
                            Float.parseFloat(splitString[6]),
                            Float.parseFloat(splitString[7])));
                } else {
                    br.close();
                    throw new Exception("Sol file planet/moon input not correct");
                }
            }
            br.close();
        } catch (FileNotFoundException e) {
            System.out.println("Sorry the file " + e + " could not be found");
            System.exit(0);
        } catch (Exception e) {
            System.out.println(e);
            System.exit(0);
        }
    }

    /**
     * Attempt to locate .sol file
     * 
     * @param fileName
     * @return
     * @throws FileNotFoundException
     */
    public File locateFile(String fileName) throws FileNotFoundException {
        File solFile = new File(fileName);
        if (!solFile.exists() || solFile.isDirectory()) {
            throw new FileNotFoundException(fileName);
        }
        return solFile;
    }

    /*
     * A class designed to package up and structure all the elements of a solar
     * system (interpreted from a .sol file) in one easily traversable way. Consists
     * of the sun and a list of all planet objects
     */
    public class StellarSystem {
        CelestialObject sun;
        ArrayList<PlanetSystem> planets;

        StellarSystem(CelestialObject sun) {
            this.sun = sun;
            this.planets = new ArrayList<PlanetSystem>();
        }

        public void addPlanetSystem(PlanetSystem planetSystem) {
            this.planets.add(planetSystem);
        }
    }

    /*
     * A class designed to store planetary systems including the main planet and the
     * moons that orbit it.
     */
    public class PlanetSystem {
        CelestialObject planet;
        ArrayList<CelestialObject> moons;

        PlanetSystem(CelestialObject planet) {
            this.planet = planet;
            this.moons = new ArrayList<CelestialObject>();
        }

        public void addMoon(CelestialObject moon) {
            this.moons.add(moon);
        }
    }

    /*
     * A class designed generalize and bundle all attributes of all celestial
     * objects present in the model and produced from the .sol file
     */
    public class CelestialObject {
        public String texture; // Texture of the object
        public float radius; // The radius of the object
        public float rotationPeriod; // Rotation in seconds
        public float distanceFromSun; // Distance from the center of the sun to the center of the celestial object
        public float orbitalPeriod; // Orbit time in seconds
        public float specularShine; // The specular shine of the object (0 being no specular component)
        public int textureID; // The id of the planet as
        public Matrix4f scale; // The scale of the object
        public Matrix4f translate;
        public Matrix4f rotate;
        public Matrix4f modelMatrix;

        // Constructor for the systems sun
        CelestialObject(String texture, float radius, float rotationPeriod) {
            this.texture = texture;
            this.radius = radius;
            this.rotationPeriod = rotationPeriod;
            this.textureID = loadTexture(this.texture); // Load the texture end

            this.scale = new Matrix4f().scale(radius);
            this.rotate = new Matrix4f();
            this.modelMatrix = new Matrix4f().scale(this.radius);

            gl.glBindTexture(GL_TEXTURE_2D, this.textureID);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            
        }

        // Constructor for planets and moons
        CelestialObject(String texture, float radius, float rotationPeriod,
                float distanceFromSun, float orbitalPeriod, float specularShine) {
            this.texture = texture;
            this.radius = radius;
            this.rotationPeriod = rotationPeriod;
            this.distanceFromSun = distanceFromSun;
            this.orbitalPeriod = -orbitalPeriod; // Negative to go counter counterclockwise when positive
            this.specularShine = specularShine;

            this.scale = new Matrix4f().scale(radius); // Scale the size of the celestial object
            this.translate = new Matrix4f().translate(this.distanceFromSun, 0, 0);
            this.rotate = new Matrix4f();

            // Create initial model view matrix
            this.modelMatrix = new Matrix4f().mul(this.translate).mul(this.scale);

            this.textureID = loadTexture(this.texture); // Load the texture end
            gl.glBindTexture(GL_TEXTURE_2D, this.textureID);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        }

        /*
         * A method designed to update the model matrix based on time. Given that the
         * period of rotation and orbit period are given these can be parametrized based
         * on time and updated as needed to ensure planetary orbits and rotations are
         * based on time rather than how quick frames can render which can lead to
         * inaccuracy based on runtime and other computational factors
         */
        public void updateObjectModelMatrix(Matrix4f planetTranslation) {
            this.translate.set(getPlanetPosition(this.translate, this.orbitalPeriod, this.distanceFromSun));
            if (planetTranslation != null) {
                this.translate.mul(planetTranslation);
            }
            this.rotate.set(getObjectRotation(this.rotate, this.rotationPeriod));
            this.modelMatrix = new Matrix4f().mul(this.translate).mul(rotate).mul(this.scale);
        }

        /*
         * A method designed to update the suns rotation. Based on parametrization in
         * time to reduce hardware inaccuracy
         */
        public void updateSunModelMatrix() {
            this.rotate.set(getObjectRotation(this.rotate, this.rotationPeriod));
            this.modelMatrix = new Matrix4f().mul(rotate).mul(this.scale);
        }
    }

    /*
     * A method to generated the necessary parameterized rotation of a celestial
     * object based on rotation speed.
     */
    public Matrix4f getObjectRotation(Matrix4f rotation, float rotationPeriod) {
        rotation.identity();
        // Percentage of the rotation complected
        this.amountOfOrbitalRotation = (this.timeElapsed % rotationPeriod) / rotationPeriod;
        // Convert to radians
        this.amountOfObjectRotation = (Math.PI * 2) * this.amountOfOrbitalRotation;
        // Return the correctly applied rotation
        return rotation.rotateY((float) this.amountOfObjectRotation);
    }

    /*
     * A method to generated the necessary parameterized translation of a celestial
     * object based on its orbital speed.
     */
    public Matrix4f getPlanetPosition(Matrix4f translate, float orbitalPeriod, float distanceFromSun) {
        translate.identity();
        // Percentage of orbit complected
        this.timeOrbitAmount = (this.timeElapsed % orbitalPeriod) / orbitalPeriod;
        // Convert rotation radians
        this.amountOfOrbitalRotation = (Math.PI * 2) * this.timeOrbitAmount;
        // Get the translation the object should be at
        translate.translate(
                (float) (Math.cos(this.amountOfOrbitalRotation) * distanceFromSun),
                0,
                (float) (Math.sin(this.amountOfOrbitalRotation) * distanceFromSun));
        return translate;
    }

    /*
     * A function to load textures into the program safely
     */
    public int loadTexture(String textureFileName) {
        GL4 gl = (GL4) GLContext.getCurrentGL();
        int finalTextureRef;
        Texture tex = null;
        try {
            tex = TextureIO.newTexture(new File(textureFileName), false);
        } catch (Exception e) {
            System.out.println(
                    "Sorry your textures did not load correctly. Please make sure they are spelled and the texture files are in the correct specified location or that they exist");
            System.exit(0);
            // super.dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
            // TODO fix this!!
        }
        finalTextureRef = tex.getTextureObject();

        // building a mipmap and use anisotropic filtering
        gl.glBindTexture(GL_TEXTURE_2D, finalTextureRef);
        gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        gl.glGenerateMipmap(GL_TEXTURE_2D);
        if (gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic")) {
            float anisoset[] = new float[1];
            gl.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, anisoset, 0);
            gl.glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, anisoset[0]);
        }
        return finalTextureRef;
    }

    /*
     * Method to import planetary model (isosphere)
     */
    private void initalizeModels() {
        // Temporatry Red Square:

        GL4 gl = (GL4) GLContext.getCurrentGL();
        this.planetModel = new ImportedModel("planet.obj");

        this.numObjVertices = planetModel.getNumVertices();
        Vector3f[] vertices = planetModel.getVertices();
        Vector2f[] texCoords = planetModel.getTexCoords();
        Vector3f[] normals = planetModel.getNormals();

        float[] pvalues = new float[numObjVertices * 3]; // vertex positions
        float[] tvalues = new float[numObjVertices * 2]; // texture coordinates
        float[] nvalues = new float[numObjVertices * 3]; // normal vectors

        for (int i = 0; i < numObjVertices; i++) {
            pvalues[i * 3] = (float) (vertices[i]).x();
            pvalues[i * 3 + 1] = (float) (vertices[i]).y();
            pvalues[i * 3 + 2] = (float) (vertices[i]).z();
            tvalues[i * 2] = (float) (texCoords[i]).x();
            tvalues[i * 2 + 1] = (float) (texCoords[i]).y();
            nvalues[i * 3] = (float) (normals[i]).x();
            nvalues[i * 3 + 1] = (float) (normals[i]).y();
            nvalues[i * 3 + 2] = (float) (normals[i]).z();
        }

        // Set up Vao
        gl.glGenVertexArrays(vao.length, vao, 0);
        gl.glBindVertexArray(vao[0]);
        gl.glGenBuffers(vbo.length, vbo, 0);

        // VBO for vertex locations
        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[0]);
        FloatBuffer vertBuf = Buffers.newDirectFloatBuffer(pvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, vertBuf.limit() * 4, vertBuf, GL_STATIC_DRAW);

        // VBO for texture coordinates (s,t)
        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[1]);
        FloatBuffer texBuf = Buffers.newDirectFloatBuffer(tvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, texBuf.limit() * 4, texBuf, GL_STATIC_DRAW);

        // VBO for normal vectors
        gl.glBindBuffer(GL_ARRAY_BUFFER, vbo[2]);
        FloatBuffer norBuf = Buffers.newDirectFloatBuffer(nvalues);
        gl.glBufferData(GL_ARRAY_BUFFER, norBuf.limit() * 4, norBuf, GL_STATIC_DRAW);
    }
}
