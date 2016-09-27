package com.cloudoki.vuforiaplugin.utils;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.StringTokenizer;


public class ObjectParser extends MeshObject {

    private final static  String TAG = "ObjectParser";

    protected final String VERTEX = "v";
    protected final String FACE = "f";
    protected final String TEXCOORD = "vt";
    protected final String NORMAL = "vn";

    public static final int FLOAT_SIZE_BYTES = 4;
    public static final int INT_SIZE_BYTES   = 4;

    private Buffer mVertBuff;
    private Buffer mTexCoordBuff;
    private Buffer mNormBuff;
    private Buffer mIndBuff;

    private int indicesNumber = 0;
    private int verticesNumber = 0;

    private boolean isLoaded = false;


    public ObjectParser(String fileName, AssetManager assets) throws Exception {
        BufferedReader buffer = null;

        ObjIndexData currObjIndexData = new ObjIndexData();
        ArrayList<ObjIndexData> objIndices = new ArrayList<ObjIndexData>();

        ArrayList<Float> vertices = new ArrayList<Float>();
        ArrayList<Float> texCoords = new ArrayList<Float>();
        ArrayList<Float> normals = new ArrayList<Float>();
        boolean currentObjHasFaces = false;

        try {
            InputStream inputStream = assets.open(fileName);
            buffer = new BufferedReader(new InputStreamReader(inputStream));

            String line;

            while((line = buffer.readLine()) != null) {
                // Skip comments and empty lines.
                if(line.length() == 0 || line.charAt(0) == '#')
                    continue;
                StringTokenizer parts = new StringTokenizer(line, " ");
                int numTokens = parts.countTokens();

                if(numTokens == 0)
                    continue;
                String type = parts.nextToken();

                if(type.equals(VERTEX)) {
                    vertices.add(Float.parseFloat(parts.nextToken()));
                    vertices.add(Float.parseFloat(parts.nextToken()));
                    vertices.add(Float.parseFloat(parts.nextToken()));
                } else if(type.equals(FACE)) {
                    currentObjHasFaces=true;
                    boolean isQuad = numTokens == 5;
                    int[] quadvids = new int[4];
                    int[] quadtids = new int[4];
                    int[] quadnids = new int[4];

                    boolean emptyVt = line.indexOf("//") > -1;
                    if(emptyVt) line = line.replace("//", "/");

                    parts = new StringTokenizer(line);

                    parts.nextToken();
                    StringTokenizer subParts = new StringTokenizer(parts.nextToken(), "/");
                    int partLength = subParts.countTokens();

                    boolean hasuv = partLength >= 2 && !emptyVt;
                    boolean hasn = partLength == 3 || (partLength == 2 && emptyVt);
                    int idx;

                    for (int i = 1; i < numTokens; i++) {
                        if(i > 1)
                            subParts = new StringTokenizer(parts.nextToken(), "/");
                        idx = Integer.parseInt(subParts.nextToken());

                        if(idx < 0) idx = (vertices.size() / 3) + idx;
                        else idx -= 1;
                        if(!isQuad)
                            currObjIndexData.vertexIndices.add(idx);
                        else
                            quadvids[i-1] = idx;
                        if (hasuv) {
                            idx = Integer.parseInt(subParts.nextToken());
                            if(idx < 0) idx = (texCoords.size() / 2) + idx;
                            else idx -= 1;
                            if(!isQuad)
                                currObjIndexData.texCoordIndices.add(idx);
                            else
                                quadtids[i-1] = idx;
                        }
                        if (hasn) {
                            idx = Integer.parseInt(subParts.nextToken());
                            if(idx < 0) idx = (normals.size() / 3) + idx;
                            else idx -= 1;
                            if(!isQuad)
                                currObjIndexData.normalIndices.add(idx);
                            else
                                quadnids[i-1] = idx;
                        }
                    }

                    if(isQuad) {
                        int[] indices = new int[] { 0, 1, 2, 0, 2, 3 };

                        for(int i=0; i<6; ++i) {
                            int index = indices[i];
                            currObjIndexData.vertexIndices.add(quadvids[index]);
                            currObjIndexData.texCoordIndices.add(quadtids[index]);
                            currObjIndexData.normalIndices.add(quadnids[index]);
                        }
                    }
                } else if(type.equals(TEXCOORD)) {
                    texCoords.add(Float.parseFloat(parts.nextToken()));
                    texCoords.add(1f - Float.parseFloat(parts.nextToken()));
                } else if(type.equals(NORMAL)) {
                    normals.add(Float.parseFloat(parts.nextToken()));
                    normals.add(Float.parseFloat(parts.nextToken()));
                    normals.add(Float.parseFloat(parts.nextToken()));
                }
            }
            buffer.close();

            if(currentObjHasFaces) {
                objIndices.add(currObjIndexData);
            }


        } catch (IOException e) {
            Logger.e(TAG, e.getMessage());
        }

        int numObjects = objIndices.size();

        for(int j=0; j<numObjects; ++j) {
            ObjIndexData oid = objIndices.get(j);

            int i;
            float[] aVertices 	= new float[oid.vertexIndices.size() * 3];
            float[] aTexCoords 	= new float[oid.texCoordIndices.size() * 2];
            float[] aNormals 	= new float[oid.normalIndices.size() * 3];
            int[] aIndices 		= new int[oid.vertexIndices.size()];

            for(i=0; i<oid.vertexIndices.size(); ++i) {
                int faceIndex = oid.vertexIndices.get(i) * 3;
                int vertexIndex = i * 3;
                try {
                    aVertices[vertexIndex] = vertices.get(faceIndex);
                    aVertices[vertexIndex+1] = vertices.get(faceIndex + 1);
                    aVertices[vertexIndex+2] = vertices.get(faceIndex + 2);
                    aIndices[i] = i;
                } catch(ArrayIndexOutOfBoundsException e) {
                    Logger.e(TAG, "Obj array index out of bounds: " + vertexIndex + ", " + faceIndex);
                }
            }
            if(texCoords != null && texCoords.size() > 0) {
                for(i=0; i<oid.texCoordIndices.size(); ++i) {
                    int texCoordIndex = oid.texCoordIndices.get(i) * 2;
                    int ti = i * 2;
                    aTexCoords[ti] = texCoords.get(texCoordIndex);
                    aTexCoords[ti + 1] = texCoords.get(texCoordIndex + 1);
                }
            }
            for(i=0; i<oid.colorIndices.size(); ++i) {
                int colorIndex = oid.colorIndices.get(i) * 4;
                int ti = i * 4;
                aTexCoords[ti] = texCoords.get(colorIndex);
                aTexCoords[ti + 1] = texCoords.get(colorIndex + 1);
                aTexCoords[ti + 2] = texCoords.get(colorIndex + 2);
                aTexCoords[ti + 3] = texCoords.get(colorIndex + 3);
            }
            for(i=0; i<oid.normalIndices.size(); ++i){
                int normalIndex = oid.normalIndices.get(i) * 3;
                int ni = i * 3;
                if(normals.size() == 0) {
                    Logger.e(TAG, "["+getClass().getName()+"] There are no normals specified for this model. Please re-export with normals.");
                    throw new Exception();
                }
                aNormals[ni] = normals.get(normalIndex);
                aNormals[ni+1] = normals.get(normalIndex + 1);
                aNormals[ni+2] = normals.get(normalIndex + 2);
            }

            setVerts(aVertices);
            setTexCoords(aTexCoords);
            setNorms(aNormals);
            setIndices(aIndices);

            isLoaded = true;
        }
    }


    public boolean didLoad() {
        return isLoaded;
    }

    private void setVerts(float[] vertex) {
        if (mVertBuff != null) {
            mVertBuff.clear();
        }
        mVertBuff = ByteBuffer
                .allocateDirect(vertex.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();

        ((FloatBuffer) mVertBuff).put(vertex);
        mVertBuff.position(0);

        // mVertBuff = fillBuffer(vertex);
        verticesNumber = vertex.length / 3;
    }


    private void setTexCoords(float[] textureCoords) {
        if (textureCoords == null) {
            return;
        }

        if (mTexCoordBuff == null) {
            mTexCoordBuff = ByteBuffer
                    .allocateDirect(textureCoords.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            ((FloatBuffer)mTexCoordBuff).put(textureCoords);
            mTexCoordBuff.position(0);
        } else {
            ((FloatBuffer) mTexCoordBuff).put(textureCoords);
        }

        // mTexCoordBuff = fillBuffer(textureCoords);
    }


    private void setNorms(float[] normals) {
        if (normals == null) {
            return;
        }

        if (mNormBuff == null) {
            mNormBuff = ByteBuffer.allocateDirect(normals.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            ((FloatBuffer) mNormBuff).put(normals);
            mNormBuff.position(0);
        } else {
            mNormBuff.position(0);
            ((FloatBuffer) mNormBuff).put(normals);
            mNormBuff.position(0);
        }

        // mNormBuff = fillBuffer(normals);
    }


    private void setIndices(int[] indices) {
        if (mIndBuff == null) {
            mIndBuff = ByteBuffer.allocateDirect(indices.length * INT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asIntBuffer();
            ((IntBuffer) mIndBuff).put(indices).position(0);

            indicesNumber = indices.length;
        } else {
            ((IntBuffer) mIndBuff).put(indices);
        }

//        short[] OBJECT_INDICES = new short[indices.length];
//        for (int i = 0; i < OBJECT_INDICES.length; i++) {
//            OBJECT_INDICES[i] = (short) indices[i];
//        }
//        mIndBuff = fillBuffer(OBJECT_INDICES);
//        indicesNumber = OBJECT_INDICES.length;
    }

    @Override
    public Buffer getBuffer(BUFFER_TYPE bufferType) {
        Buffer result = null;
        switch (bufferType) {
            case BUFFER_TYPE_VERTEX:
                result = mVertBuff;
                break;
            case BUFFER_TYPE_TEXTURE_COORD:
                result = mTexCoordBuff;
                break;
            case BUFFER_TYPE_NORMALS:
                result = mNormBuff;
                break;
            case BUFFER_TYPE_INDICES:
                result = mIndBuff;
            default:
                break;
        }
        return result;
    }

    @Override
    public int getNumObjectVertex() {
        return verticesNumber;
    }

    @Override
    public int getNumObjectIndex() {
        return indicesNumber;
    }


    protected class ObjIndexData {

        public ArrayList<Integer> vertexIndices;
        public ArrayList<Integer> texCoordIndices;
        public ArrayList<Integer> colorIndices;
        public ArrayList<Integer> normalIndices;

        public ObjIndexData() {
            vertexIndices = new ArrayList<Integer>();
            texCoordIndices = new ArrayList<Integer>();
            colorIndices = new ArrayList<Integer>();
            normalIndices = new ArrayList<Integer>();
        }
    }
}
