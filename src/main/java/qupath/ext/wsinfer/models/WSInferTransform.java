/**
 * Copyright 2023 University of Edinburgh
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

package qupath.ext.wsinfer.models;

import java.util.Map;

/**
 * A single preprocessing transform to use with a WSInfer model.
 */
public class WSInferTransform {

    private String name;
    private Map<String, Object> arguments;

    /**
     * Name of the transform.
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * Optional arguments associated with the transform.
     * @return
     */
    public Map<String, Object> getArguments() {
        return arguments;
    }
}
