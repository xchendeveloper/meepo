/**
 * Copyright 2014-2017 yangming.liu<bytefox@126.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.feisoft.jta.supports.dubbo;

import org.feisoft.jta.supports.dubbo.validator.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.*;

public class DubboConfigPostProcessor implements BeanFactoryPostProcessor {
	static final Logger logger = LoggerFactory.getLogger(DubboConfigPostProcessor.class);

	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();

		List<BeanDefinition> appNameList = new ArrayList<BeanDefinition>();
		List<BeanDefinition> providerList = new ArrayList<BeanDefinition>();
		List<BeanDefinition> consumerList = new ArrayList<BeanDefinition>();
		List<BeanDefinition> protocolList = new ArrayList<BeanDefinition>();
		List<BeanDefinition> registryList = new ArrayList<BeanDefinition>();

		Map<String, BeanDefinition> serviceMap = new HashMap<String, BeanDefinition>();
		Map<String, BeanDefinition> referenceMap = new HashMap<String, BeanDefinition>();

		Map<String, Class<?>> clazzMap = new HashMap<String, Class<?>>();

		String[] beanNameArray = beanFactory.getBeanDefinitionNames();
		for (int i = 0; i < beanNameArray.length; i++) {
			String beanName = beanNameArray[i];
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			String beanClassName = beanDef.getBeanClassName();

			Class<?> beanClass = null;
			try {
				beanClass = cl.loadClass(beanClassName);
			} catch (Exception ex) {
				logger.debug("Cannot load class {}, beanId= {}!", beanClassName, beanName, ex);
				continue;
			}

			clazzMap.put(beanClassName, beanClass);
		}

		for (int i = 0; i < beanNameArray.length; i++) {
			String beanName = beanNameArray[i];
			BeanDefinition beanDef = beanFactory.getBeanDefinition(beanName);
			String beanClassName = beanDef.getBeanClassName();

			Class<?> beanClass = clazzMap.get(beanClassName);

			if (com.alibaba.dubbo.config.ApplicationConfig.class.equals(beanClass)) {
				appNameList.add(beanDef);
			} else if (com.alibaba.dubbo.config.ProtocolConfig.class.equals(beanClass)) {
				protocolList.add(beanDef);
			} else if (com.alibaba.dubbo.config.RegistryConfig.class.equals(beanClass)) {
				registryList.add(beanDef);
			} else if (com.alibaba.dubbo.config.ProviderConfig.class.equals(beanClass)) {
				providerList.add(beanDef);
			} else if (com.alibaba.dubbo.config.ConsumerConfig.class.equals(beanClass)) {
				consumerList.add(beanDef);
			} else if (com.alibaba.dubbo.config.spring.ServiceBean.class.equals(beanClass)) {
				MutablePropertyValues mpv = beanDef.getPropertyValues();
				PropertyValue group = mpv.getPropertyValue("group");
				if (group == null || group.getValue() == null //
						|| ("org.feisoft.jta".equals(group.getValue())
								|| String.valueOf(group.getValue()).startsWith("org.feisoft.jta-")) == false) {
					continue;
				}

				serviceMap.put(beanName, beanDef);
			} else if (com.alibaba.dubbo.config.spring.ReferenceBean.class.equals(beanClass)) {
				MutablePropertyValues mpv = beanDef.getPropertyValues();
				PropertyValue group = mpv.getPropertyValue("group");
				if (group == null || group.getValue() == null //
						|| ("org.feisoft.jta".equals(group.getValue())
								|| String.valueOf(group.getValue()).startsWith("org.feisoft.jta-")) == false) {
					continue;
				}

				referenceMap.put(beanName, beanDef);
			}
		}

		Set<BeanDefinition> providerSet = new HashSet<BeanDefinition>();
		Set<BeanDefinition> protocolSet = new HashSet<BeanDefinition>();
		for (Iterator<Map.Entry<String, BeanDefinition>> itr = serviceMap.entrySet().iterator(); itr.hasNext();) {
			Map.Entry<String, BeanDefinition> entry = itr.next();
			BeanDefinition beanDef = entry.getValue();
			MutablePropertyValues mpv = beanDef.getPropertyValues();
			PropertyValue provider = mpv.getPropertyValue("provider");
			PropertyValue protocol = mpv.getPropertyValue("protocol");

			String providerValue = provider == null ? null : String.valueOf(provider.getValue());
			if (providerValue == null) {
				if (providerList.size() > 0) {
					providerSet.add(providerList.get(0));
				}
			} else if ("N/A".equals(providerValue) == false) {
				String[] keyArray = providerValue.split("\\s*,\\s*");
				for (int j = 0; j < keyArray.length; j++) {
					String key = keyArray[j];
					BeanDefinition def = beanFactory.getBeanDefinition(key);
					providerSet.add(def);
				}
			}

			String protocolValue = protocol == null ? null : String.valueOf(protocol.getValue());
			if (protocolValue == null) {
				if (protocolList.size() > 0) {
					protocolSet.add(protocolList.get(0));
				}
			} else if ("N/A".equals(protocolValue) == false) {
				String[] keyArray = protocolValue.split("\\s*,\\s*");
				for (int i = 0; i < keyArray.length; i++) {
					String key = keyArray[i];
					BeanDefinition def = beanFactory.getBeanDefinition(key);
					protocolSet.add(def);
				}
			}
		}

		ApplicationConfigValidator appConfigValidator = new ApplicationConfigValidator();
		appConfigValidator.setDefinitionList(appNameList);
		appConfigValidator.validate();

		if (protocolList.size() == 0) {
			throw new FatalBeanException("There is no protocol config specified!");
		}

		for (Iterator<BeanDefinition> itr = protocolSet.iterator(); itr.hasNext();) {
			BeanDefinition beanDef = itr.next();
			ProtocolConfigValidator validator = new ProtocolConfigValidator();
			validator.setBeanDefinition(beanDef);
			validator.validate();
		}

		for (Iterator<BeanDefinition> itr = providerSet.iterator(); itr.hasNext();) {
			BeanDefinition beanDef = itr.next();
			ProviderConfigValidator validator = new ProviderConfigValidator();
			validator.setBeanDefinition(beanDef);
			validator.validate();
		}

		for (Iterator<Map.Entry<String, BeanDefinition>> itr = serviceMap.entrySet().iterator(); itr.hasNext();) {
			Map.Entry<String, BeanDefinition> entry = itr.next();
			ServiceConfigValidator validator = new ServiceConfigValidator();
			validator.setBeanName(entry.getKey());
			validator.setBeanDefinition(entry.getValue());
			validator.validate(); // retries, loadbalance, cluster, filter, group
		}

		for (Iterator<Map.Entry<String, BeanDefinition>> itr = referenceMap.entrySet().iterator(); itr.hasNext();) {
			Map.Entry<String, BeanDefinition> entry = itr.next();
			ReferenceConfigValidator validator = new ReferenceConfigValidator();
			validator.setBeanName(entry.getKey());
			validator.setBeanDefinition(entry.getValue());
			validator.validate(); // retries, loadbalance, cluster, filter
		}
	}

}
